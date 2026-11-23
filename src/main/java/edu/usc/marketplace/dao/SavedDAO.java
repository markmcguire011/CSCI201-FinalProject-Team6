package edu.usc.marketplace.dao;

import edu.usc.marketplace.model.Listing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the saved_listings table (the heart button). */
public class SavedDAO extends BaseDAO<Listing> {
    private final ListingDAO listings;

    public SavedDAO(Database db) {
        super(db);
        listings = new ListingDAO(db);
    }

    public SavedDAO() {
        this(Database.get());
    }

    @Override
    protected Listing mapRow(ResultSet rs) throws SQLException {
        return listings.mapRow(rs);
    }

    /** Saving twice keeps one row (INSERT IGNORE), so double-clicking the heart is harmless. */
    public void save(int userId, int listingId) {
        update("INSERT IGNORE INTO saved_listings (user_id, listing_id) VALUES (?, ?)", userId, listingId);
    }

    public void unsave(int userId, int listingId) {
        update("DELETE FROM saved_listings WHERE user_id = ? AND listing_id = ?", userId, listingId);
    }

    public boolean isSaved(int userId, int listingId) {
        return queryLong("SELECT COUNT(*) FROM saved_listings WHERE user_id = ? AND listing_id = ?", userId, listingId) > 0;
    }

    /** The Saved page, most recently saved first. Listings removed by an administrator are left out. */
    public List<Listing> findSaved(int userId) {
        return queryList(
                "SELECT l.*, u.full_name AS seller_name, "
                + "(SELECT p.file_path FROM listing_photos p WHERE p.listing_id = l.listing_id ORDER BY p.sort_order LIMIT 1) AS cover_photo, "
                + "(SELECT AVG(stars) FROM reviews WHERE reviewed_user_id = l.seller_id AND deleted_by_admin = FALSE) AS seller_rating "
                + "FROM saved_listings s JOIN listings l ON l.listing_id = s.listing_id JOIN users u ON u.user_id = l.seller_id "
                + "WHERE s.user_id = ? AND l.deleted_by_admin = FALSE ORDER BY s.saved_at DESC", userId);
    }

    /** Who saved this listing, so they can be notified when it changes. */
    public List<Integer> findUsersWhoSaved(int listingId) {
        List<Integer> ids = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT user_id FROM saved_listings WHERE listing_id = ?")) {
            bind(ps, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
        return ids;
    }
}
