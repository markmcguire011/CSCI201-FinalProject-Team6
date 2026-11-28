package edu.usc.marketplace.dao;

import edu.usc.marketplace.exception.*;
import edu.usc.marketplace.model.*;
import edu.usc.marketplace.service.OwnershipGuard;
import edu.usc.marketplace.storage.FileStorageService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/** Listing mutations serialize on the same listing row as edits and moderation. */
public class ListingActions extends ListingDAO {
    private static final Logger LOG = Logger.getLogger(ListingActions.class.getName());
    private static final String STALE = "deleted_by_admin = FALSE AND status IN ('AVAILABLE','PENDING','INACTIVE') "
            + "AND last_activity_at <= NOW() - INTERVAL 60 DAY";
    private final ConversationDAO conversations;
    private final NotificationDAO notifications;
    private final FileStorageService storage;

    public ListingActions() {
        this(Database.get(), FileStorageService.get());
    }

    public ListingActions(Database db, FileStorageService storage) {
        super(db);
        this.storage = storage;
        conversations = new ConversationDAO(db);
        notifications = new NotificationDAO(db);
    }

    private Listing lock(Connection c, int id) throws SQLException {
        Listing l = findForUpdate(c, id);
        if (l == null || l.isDeletedByAdmin()) throw new NotFoundException("Listing not found.");
        return l;
    }

    public boolean setSaved(int user, int id, boolean desired) {
        return db.inTransaction(c -> {
            if (desired) {
                Listing l = lock(c, id);
                if (l.getSellerId() == user) throw new ForbiddenException("You can't save your own listing.");
                if (!l.getStatus().isPublic()) throw new ConflictException("This listing is no longer available.");
                update(c, "INSERT INTO saved_listings (user_id,listing_id) VALUES (?,?) "
                        + "ON DUPLICATE KEY UPDATE saved_at = saved_at", user, id);
            } else {
                update(c, "DELETE FROM saved_listings WHERE user_id=? AND listing_id=?", user, id);
            }
            return desired;
        });
    }

    public int startConversation(int user, int id) {
        return db.inTransaction(c -> {
            Listing l = lock(c, id);
            if (l.getSellerId() == user) throw new ForbiddenException("You can't message yourself about your own listing.");
            if (!l.getStatus().canMessage()) throw new ConflictException("Messaging is turned off for this listing.");
            return conversations.findOrCreate(c, id, user, l.getSellerId());
        });
    }

    public record StatusResult(ListingStatus status, int version) {}

    static void checkChange(Member seller, Listing l, ListingStatus status, int version, Integer buyer, UploadedFile proof) {
        OwnershipGuard.requireOwner(seller, l.getSellerId());
        if (l.getVersion() != version) throw new ConflictException("Refresh required");
        if (l.getBuyerId() != null && ((buyer != null && !buyer.equals(l.getBuyerId())) || proof != null)) {
            throw new ValidationException("The original buyer and payment proof must be kept.");
        }
        if (l.getStatus() == ListingStatus.EXPIRED && status != ListingStatus.AVAILABLE) {
            throw new ValidationException("Reactivate an expired listing as Available first.");
        }
    }

    public StatusResult change(Member seller, int id, ListingStatus status, int version, Integer buyer, UploadedFile proof) {
        if (status == null || status == ListingStatus.EXPIRED) throw new ValidationException("Choose a seller status.");
        String[] staged = new String[1];
        try {
            return db.inTransaction(c -> {
                Listing l = lock(c, id);
                checkChange(seller, l, status, version, buyer, proof);
                if (status == l.getStatus()) return new StatusResult(status, version);
                boolean firstSale = status == ListingStatus.SOLD && l.getBuyerId() == null;
                if (firstSale) {
                    if (!l.getStatus().canMessage()) throw new ConflictException("Make the listing Available before recording a sale.");
                    if (buyer == null || buyer == seller.getId() || queryLong(c,
                            "SELECT COUNT(*) FROM conversations c WHERE c.listing_id=? AND c.buyer_id=? AND c.seller_id=? "
                            + "AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id=c.conversation_id AND m.sender_id=c.buyer_id)",
                            id, buyer, seller.getId()) == 0) throw new ValidationException("Choose a buyer from this listing's conversations.");
                    if (proof == null) throw new ValidationException("Select a JPG, PNG or PDF payment proof.");
                    try {
                        staged[0] = storage.saveProof(proof.fileName(), proof.stream());
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                    insert(c, "INSERT INTO payment_proofs (listing_id,uploader_id,storage_key,detected_type,byte_size) VALUES (?,?,?,?,?)",
                            id, seller.getId(), staged[0], storage.contentType(java.nio.file.Path.of(staged[0])), proof.size());
                    if (!markSold(c, id, buyer, staged[0], version)) throw new ConflictException("Refresh required");
                } else {
                    update(c, "UPDATE listings SET status=?, version=version+1, last_activity_at=NOW() WHERE listing_id=?", status, id);
                }
                recordChange(c, id, version + 1, status, seller.getId(), firstSale ? buyer : null);
                return new StatusResult(status, version + 1);
            });
        } catch (RuntimeException ex) {
            if (staged[0] != null) {
                try { storage.deleteProof(staged[0]); }
                catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            }
            throw ex;
        }
    }

    private void recordChange(Connection c, int id, int version, ListingStatus status, int seller, Integer firstBuyer)
            throws SQLException {
        String link = "index.html?listing=" + id;
        insert(c, "INSERT INTO listing_events (listing_id,version,event_type) VALUES (?,?,'ListingStatusChanged')", id, version);
        if (status == ListingStatus.SOLD) {
            update(c, "INSERT INTO notifications (user_id,type,text,link) "
                    + "SELECT user_id,'LISTING_SOLD','A listing you saved was sold.',? FROM saved_listings WHERE listing_id=?", link, id);
        } else if (status == ListingStatus.EXPIRED) {
            notifications.insert(c, seller, "LISTING_EXPIRED", "Your listing expired after 60 days without activity.", link);
        }
        if (firstBuyer != null) {
            insert(c, "INSERT INTO listing_events (listing_id,version,event_type,recipient_id) VALUES (?,?,'BuyerReviewRequested',?)",
                    id, version, firstBuyer);
            notifications.insert(c, firstBuyer, "REVIEW_REQUEST", "Your purchase is complete. You can review the seller.", link);
        }
    }

    public void expireInactiveListings() {
        int afterId = 0;
        while (true) {
            final int cursor = afterId;
            List<Integer> ids = db.inTransaction(c -> {
                var batch = new java.util.ArrayList<Integer>();
                try (var ps = c.prepareStatement("SELECT listing_id FROM listings WHERE " + STALE
                        + " AND listing_id > ? ORDER BY listing_id LIMIT 100")) {
                    ps.setInt(1, cursor);
                    try (var rows = ps.executeQuery()) { while (rows.next()) batch.add(rows.getInt(1)); }
                }
                return batch;
            });
            if (ids.isEmpty()) return;
            for (int id : ids) {
                afterId = id;
                try {
                    db.inTransaction(c -> {
                        Listing l = findForUpdate(c, id);
                        if (l != null && update(c, "UPDATE listings SET status='EXPIRED',version=version+1 WHERE listing_id=? AND " + STALE, id) == 1) {
                            recordChange(c, id, l.getVersion()+1, ListingStatus.EXPIRED, l.getSellerId(), null);
                        }
                        return null;
                    });
                } catch (RuntimeException ex) {
                    LOG.log(java.util.logging.Level.WARNING, "Could not expire listing " + id, ex);
                }
            }
        }
    }
}
