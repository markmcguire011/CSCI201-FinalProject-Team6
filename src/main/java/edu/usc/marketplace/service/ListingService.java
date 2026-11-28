package edu.usc.marketplace.service;

import edu.usc.marketplace.dao.DataException;
import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.dao.PhotoDAO;
import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.Category;
import edu.usc.marketplace.model.ItemCondition;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.ListingDetail;
import edu.usc.marketplace.model.ListingForm;
import edu.usc.marketplace.model.ListingPhoto;
import edu.usc.marketplace.model.ListingStatus;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.storage.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.IIOException;

/**
 * Creating, reading and editing a listing (section 7). Creating and editing work the same way:
 *
 *   1. The form and photos are checked first; an invalid form writes no file and runs no SQL.
 *   2. The new photos are saved, in the order the seller chose.
 *   3. The listing and its photo rows are saved in one transaction, so a listing never exists without
 *      its photos. A transaction MySQL stops with a deadlock is tried again (see inTransactionWithRetry).
 *      If it still fails, it is rolled back and the newly saved files are deleted.
 *
 * The seller is always the logged-in member, never someone named in the form.
 */
public class ListingService {
    private static final Logger LOG = Logger.getLogger(ListingService.class.getName());

    public static final String NOT_FOUND = "Listing not found.";
    public static final String CHANGED = "This listing was just changed. Refresh and try again.";
    public static final String PHOTOS_OUT_OF_DATE = "Photo list is out of date. Refresh and try again.";

    /** How many times a transaction stopped by a deadlock is tried in all. */
    public static final int MAX_ATTEMPTS = 4;
    private static final int MYSQL_DEADLOCK = 1213;

    /** Runs statements as one transaction. The app uses Database.inTransaction; tests run the work directly. */
    @FunctionalInterface
    public interface Transactions {
        <T> T inTransaction(Database.SqlWork<T> work);
    }

    private final ListingDAO listings;
    private final PhotoDAO photos;
    private final FileStorageService storage;
    private final Transactions transactions;

    /** Used by the app: the shared database pool and upload folder. */
    public ListingService() {
        this(new ListingDAO(), new PhotoDAO(), FileStorageService.get(), Database.get()::inTransaction);
    }

    /** Used by tests. */
    public ListingService(ListingDAO listings, PhotoDAO photos, FileStorageService storage, Transactions transactions) {
        this.listings = listings;
        this.photos = photos;
        this.storage = storage;
        this.transactions = transactions;
    }

    /** Creates an Available listing for the seller and returns its id. */
    public int create(Member seller, ListingForm form) {
        Map<String, String> errors = new LinkedHashMap<>(Validator.validateListing(form));
        errors.putAll(Validator.validatePhotos(0, form.newPhotos));
        if (!errors.isEmpty()) throw new ValidationException(Validator.FIX_FIELDS, errors);

        Listing listing = listingFrom(form);
        listing.setSellerId(seller.getId());

        List<String> saved = savePhotos(form.newPhotos);
        int id;
        try {
            // Photo rows are added with insertAll, not replaceAll: replaceAll's DELETE on a brand-new listing
            // made simultaneous creates deadlock.
            id = inTransactionWithRetry("creating", c -> {
                int newId = listings.insert(c, listing);
                photos.insertAll(c, newId, saved);
                return newId;
            });
        } catch (RuntimeException e) {
            deleteQuietly(saved);
            throw e;
        }

        return id;
    }

    /**
     * One listing for the Edit Listing form and the listing pop-up. viewer is the logged-in member, or null
     * for a guest. A listing removed by an administrator is "not found".
     */
    public ListingDetail getListing(Member viewer, int id) {
        Listing listing = findVisible(id);
        if (!listing.visibleTo(viewer)) {
            throw new NotFoundException(NOT_FOUND);
        }
        return ListingDetail.from(listing, photos.findByListing(id), viewer);
    }

    /**
     * Saves the seller's edit (section 7d) and returns the listing as it is now. In this order:
     *
     *   a. the listing must exist and not be removed by an administrator (404)
     *   b. only its seller may edit it; the seller id comes from the database, never the request (403)
     *   c. only Available and Inactive listings can be edited (409), and the form must be for the current version
     *      (409): every save gives the photos new ids, so a page showing an older version would otherwise be
     *      told its photo list is out of date instead of that the listing changed
     *   d. keepPhotoIds must be photos of this listing, each once, and photoOrder (if sent) must name every
     *      kept and new photo exactly once (400)
     *   e. the form and the photo count are checked (400)
     *   -- nothing has been written up to here --
     *   f. the new photos are saved
     *   g. the listing and its photo rows are saved in one transaction; if the version changed in the
     *      meantime, nothing is saved (409)
     *   h. if the transaction fails, the new files are deleted and the existing ones are kept
     *   i. after it commits, the files of photos that were not kept are deleted
     */
    public ListingDetail update(Member member, int id, ListingForm form) {
        Listing existing = findVisible(id);
        OwnershipGuard.requireOwner(member, existing.getSellerId());
        if (existing.getStatus() == null || !existing.getStatus().isEditable()) {
            throw new ConflictException("This listing is " + statusLabel(existing.getStatus()) + ", so its details are locked.");
        }
        if (form.version != existing.getVersion()) throw new ConflictException(CHANGED);   // checked again in the UPDATE

        List<ListingPhoto> current = photos.findByListing(id);
        List<String> keptFiles = keptFiles(current, form.keepPhotoIds);
        int newCount = form.newPhotos == null ? 0 : form.newPhotos.size();
        List<Slot> order = photoOrder(form.photoOrder, form.keepPhotoIds, newCount);

        Map<String, String> errors = new LinkedHashMap<>(Validator.validateListing(form));
        errors.putAll(Validator.validatePhotos(keptFiles.size(), form.newPhotos));
        if (!errors.isEmpty()) throw new ValidationException(Validator.FIX_FIELDS, errors);

        Listing listing = listingFrom(form);
        listing.setId(id);
        listing.setSellerId(existing.getSellerId());

        List<String> saved = savePhotos(form.newPhotos);
        List<String> allFiles = arrange(order, keptFiles, saved);
        try {
            inTransactionWithRetry("editing", c -> {
                // Throwing inside the transaction rolls it back.
                if (!listings.update(c, listing, form.version)) throw new ConflictException(CHANGED);
                photos.replaceAll(c, id, allFiles);
                return null;
            });
        } catch (RuntimeException e) {
            deleteQuietly(saved);
            throw e;
        }

        List<String> dropped = current.stream().map(ListingPhoto::getFilePath).filter(f -> !keptFiles.contains(f)).toList();
        deleteQuietly(dropped);

        // TODO section 10 (Saved): if the price went down (existing.getPrice() > listing.getPrice()),
        //      notify the members who saved this listing.
        return getListing(member, id);
    }

    /** The listing, unless it is missing or was removed by an administrator. */
    private Listing findVisible(int id) {
        Listing listing = listings.findById(id);
        if (listing == null || listing.isDeletedByAdmin()) throw new NotFoundException(NOT_FOUND);
        return listing;
    }

    /**
     * The file names of the photos to keep, in the order given. Every id must be one of this listing's photos,
     * and appear once; anything else means the page was showing an older photo list.
     */
    private static List<String> keptFiles(List<ListingPhoto> current, List<Integer> keepPhotoIds) {
        Map<Integer, String> byId = new LinkedHashMap<>();
        for (ListingPhoto p : current) byId.put(p.getId(), p.getFilePath());

        List<String> kept = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer photoId : keepPhotoIds == null ? List.<Integer>of() : keepPhotoIds) {
            if (photoId == null || !byId.containsKey(photoId) || !seen.add(photoId)) throw photosOutOfDate();
            kept.add(byId.get(photoId));
        }
        return kept;
    }

    /** One place in the photo order: the index-th kept photo (in keepPhotoIds order) or the index-th new one. */
    private record Slot(boolean kept, int index) {
    }

    /**
     * Reads photoOrder ("keep:86", "new:0", ...). It must name every kept photo and every new photo exactly once;
     * anything else means the page was showing an older photo list. Returns null when photoOrder was not sent.
     */
    private static List<Slot> photoOrder(List<String> tokens, List<Integer> keepPhotoIds, int newCount) {
        if (tokens == null) return null;
        List<Integer> keep = keepPhotoIds == null ? List.of() : keepPhotoIds;
        if (tokens.size() != keep.size() + newCount) throw photosOutOfDate();

        List<Slot> order = new ArrayList<>();
        Set<Slot> seen = new HashSet<>();
        for (String token : tokens) {
            Slot slot = slot(token, keep, newCount);
            if (slot == null || !seen.add(slot)) throw photosOutOfDate();
            order.add(slot);
        }
        return order;   // as many slots as photos, none twice, so every photo is named exactly once
    }

    /** "keep:86" or "new:0" as a Slot, or null if it is not one of the photos being sent. */
    private static Slot slot(String token, List<Integer> keep, int newCount) {
        if (token == null) return null;
        try {
            if (token.startsWith("keep:")) {
                int index = keep.indexOf(Integer.parseInt(token.substring("keep:".length())));
                return index < 0 ? null : new Slot(true, index);
            }
            if (token.startsWith("new:")) {
                int index = Integer.parseInt(token.substring("new:".length()));
                return index < 0 || index >= newCount ? null : new Slot(false, index);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /** The file names in display order: as photoOrder says, or the kept photos followed by the new ones. */
    private static List<String> arrange(List<Slot> order, List<String> keptFiles, List<String> newFiles) {
        if (order == null) {
            List<String> all = new ArrayList<>(keptFiles);
            all.addAll(newFiles);
            return all;
        }
        return order.stream().map(s -> s.kept() ? keptFiles.get(s.index()) : newFiles.get(s.index())).toList();
    }

    private static ValidationException photosOutOfDate() {
        return new ValidationException(Validator.FIX_FIELDS, Map.of("photos", PHOTOS_OUT_OF_DATE));
    }

    /** The form's values in the form they are stored. Call only after Validator.validateListing found no errors. */
    private static Listing listingFrom(ListingForm form) {
        Listing listing = new Listing();
        listing.setTitle(form.title.trim());
        listing.setCategory(Category.fromString(form.category).name());
        listing.setPrice(Validator.parsePrice(form.price));
        listing.setItemCondition(ItemCondition.fromString(form.condition).name());
        listing.setCourseCode(Validator.normalizeCourseCode(form.courseCode));
        listing.setPickupSpot(form.pickupSpot.trim());
        listing.setDescription(form.description.trim());
        return listing;
    }

    /** "PENDING" becomes "Pending". */
    private static String statusLabel(ListingStatus status) {
        if (status == null) return "unavailable";
        String name = status.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Runs the work as one transaction. If MySQL stops it with a deadlock (error 1213), it has rolled the
     * transaction back completely and asks for it to be restarted, so it is tried again, up to MAX_ATTEMPTS
     * times, after a short random pause. Any other error is not retried.
     */
    private <T> T inTransactionWithRetry(String what, Database.SqlWork<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.inTransaction(work);
            } catch (DataException e) {
                if (attempt >= MAX_ATTEMPTS || !isDeadlock(e)) throw e;
                LOG.info("Deadlock while " + what + " a listing, trying again (attempt " + (attempt + 1) + " of " + MAX_ATTEMPTS + ").");
                pauseBeforeRetry(attempt, e);
            }
        }
    }

    /** MySQL's "Deadlock found when trying to get lock; try restarting transaction". */
    static boolean isDeadlock(DataException e) {
        SQLException sql = e.getSqlException();
        return sql != null && (sql.getErrorCode() == MYSQL_DEADLOCK || "40001".equals(sql.getSQLState()));
    }

    /** 20 to 80 ms times the attempt number, so the retries of requests that collided do not collide again. */
    private static void pauseBeforeRetry(int attempt, DataException cause) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(20, 81) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    /**
     * Saves each photo and returns the saved file names in the same order. If one fails, the ones
     * already saved are deleted, so a failed request leaves no files behind.
     */
    private List<String> savePhotos(List<UploadedFile> files) {
        List<String> saved = new ArrayList<>();
        if (files == null) return saved;
        for (UploadedFile file : files) {
            if (file == null) continue;
            try (InputStream in = file.stream()) {
                saved.add(storage.saveImage(file.fileName(), in));
            } catch (ValidationException | IIOException e) {
                // It starts like a JPG or PNG but cannot be opened as a picture. saveImage reports that as a
                // ValidationException, or as an IIOException when ImageIO fails partway through the file.
                deleteQuietly(saved);
                throw new ValidationException(Validator.FIX_FIELDS,
                        Map.of("photos", "\"" + file.fileName() + "\": " + Validator.PHOTO_TYPE_ERROR));
            } catch (IOException e) {
                deleteQuietly(saved);
                throw new UncheckedIOException("Could not save a listing photo", e);
            } catch (RuntimeException e) {
                deleteQuietly(saved);
                throw e;
            }
        }
        return saved;
    }

    /** A file that cannot be deleted is only logged, so the original error is the one the user sees. */
    private void deleteQuietly(List<String> fileNames) {
        for (String name : fileNames) {
            try {
                storage.deletePublic(name);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not delete the unused photo " + name, e);
            }
        }
    }
}
