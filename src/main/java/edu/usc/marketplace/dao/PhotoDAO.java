package edu.usc.marketplace.dao;

import edu.usc.marketplace.model.ListingPhoto;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes the listing_photos table (up to 5 photos per listing, in order). */
public class PhotoDAO extends BaseDAO<ListingPhoto> {
    public static final int MAX_PHOTOS = 5;

    public PhotoDAO(Database db) {
        super(db);
    }

    public PhotoDAO() {
        super();
    }

    @Override
    protected ListingPhoto mapRow(ResultSet rs) throws SQLException {
        ListingPhoto p = new ListingPhoto();
        p.setId(rs.getInt("photo_id"));
        p.setListingId(rs.getInt("listing_id"));
        p.setFilePath(rs.getString("file_path"));
        p.setSortOrder(rs.getInt("sort_order"));
        return p;
    }

    public List<ListingPhoto> findByListing(int listingId) {
        return queryList("SELECT * FROM listing_photos WHERE listing_id = ? ORDER BY sort_order", listingId);
    }

    /** Replaces all photos of a listing with the given files, in that order (used when a listing is created or edited). */
    public void replaceAll(Connection c, int listingId, List<String> filePaths) throws SQLException {
        if (filePaths.size() > MAX_PHOTOS) throw new IllegalArgumentException("A listing can have at most 5 photos.");
        update(c, "DELETE FROM listing_photos WHERE listing_id = ?", listingId);
        for (int i = 0; i < filePaths.size(); i++) {
            insert(c, "INSERT INTO listing_photos (listing_id, file_path, sort_order) VALUES (?, ?, ?)",
                    listingId, filePaths.get(i), i + 1);
        }
    }

    /**
     * Adds the photos of a listing that has none yet, in that order (sort_order 1, 2, ...). Used when a listing
     * is created: unlike replaceAll it runs no DELETE first, because on rows that do not exist yet that DELETE
     * locks the same gap of the index for every listing being created at the same moment, and the inserts then deadlock.
     */
    public void insertAll(Connection c, int listingId, List<String> filePaths) throws SQLException {
        if (filePaths.size() > MAX_PHOTOS) throw new IllegalArgumentException("A listing can have at most 5 photos.");
        for (int i = 0; i < filePaths.size(); i++) {
            insert(c, "INSERT INTO listing_photos (listing_id, file_path, sort_order) VALUES (?, ?, ?)",
                    listingId, filePaths.get(i), i + 1);
        }
    }

    public void replaceAll(int listingId, List<String> filePaths) {
        db.inTransaction(c -> {
            replaceAll(c, listingId, filePaths);
            return null;
        });
    }
}
