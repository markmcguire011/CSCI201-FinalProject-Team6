package edu.usc.marketplace.dao;

import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.model.Review;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes the reviews table: one review per sale, optionally anonymous. */
public class ReviewDAO extends BaseDAO<Review> {

    public ReviewDAO(Database db) {
        super(db);
    }

    public ReviewDAO() {
        super();
    }

    @Override
    protected Review mapRow(ResultSet rs) throws SQLException {
        Review r = new Review();
        r.setId(rs.getInt("review_id"));
        r.setListingId(rs.getInt("listing_id"));
        r.setReviewerId(rs.getInt("reviewer_id"));
        r.setReviewedUserId(rs.getInt("reviewed_user_id"));
        r.setStars(rs.getInt("stars"));
        r.setComment(rs.getString("comment"));
        r.setAnonymous(rs.getBoolean("anonymous"));
        r.setDeletedByAdmin(rs.getBoolean("deleted_by_admin"));
        r.setCreatedAt(time(rs, "created_at"));
        // The reviewer's name is only filled in when the review is not anonymous.
        r.setReviewerName(r.isAnonymous() ? null : rs.getString("reviewer_name"));
        return r;
    }

    public int insert(Review r) {
        try {
            return insert("INSERT INTO reviews (listing_id, reviewer_id, reviewed_user_id, stars, comment, anonymous) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    r.getListingId(), r.getReviewerId(), r.getReviewedUserId(), r.getStars(), r.getComment(), r.isAnonymous());
        } catch (DataException e) {
            if (e.isDuplicateKey()) throw new ConflictException("This sale has already been reviewed.");
            throw e;
        }
    }

    public Review findByListing(int listingId) {
        return queryOne(SELECT + "WHERE r.listing_id = ?", listingId);
    }

    /** Reviews shown on a user's profile, newest first. Reviews removed by an administrator are left out. */
    public List<Review> findForUser(int reviewedUserId) {
        return queryList(SELECT + "WHERE r.reviewed_user_id = ? AND r.deleted_by_admin = FALSE ORDER BY r.created_at DESC",
                reviewedUserId);
    }

    /** Average stars, or 0 when the user has no reviews yet. Rounded to one decimal, e.g. 4.5. */
    public double averageStars(int reviewedUserId) {
        long tenths = queryLong("SELECT COALESCE(ROUND(AVG(stars) * 10), 0) FROM reviews "
                + "WHERE reviewed_user_id = ? AND deleted_by_admin = FALSE", reviewedUserId);
        return tenths / 10.0;
    }

    public int count(int reviewedUserId) {
        return (int) queryLong("SELECT COUNT(*) FROM reviews WHERE reviewed_user_id = ? AND deleted_by_admin = FALSE", reviewedUserId);
    }

    /** Administrator removes a review (soft delete). */
    public boolean deleteByAdmin(int reviewId) {
        return update("UPDATE reviews SET deleted_by_admin = TRUE WHERE review_id = ?", reviewId) == 1;
    }

    private static final String SELECT =
            "SELECT r.*, u.full_name AS reviewer_name FROM reviews r JOIN users u ON u.user_id = r.reviewer_id ";
}
