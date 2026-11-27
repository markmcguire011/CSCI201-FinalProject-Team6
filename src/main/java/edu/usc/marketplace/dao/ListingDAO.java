package edu.usc.marketplace.dao;

import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.ListingStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the listings table.
 *
 * Every change checks the listing's version number. If two people change the same listing at the same
 * time, only the first change matches the version; the second changes 0 rows and the method returns false,
 * so the service can answer "This listing was just changed. Refresh and try again." (409).
 */
public class ListingDAO extends BaseDAO<Listing> {

    /** The listing plus the seller's name, first photo and average rating, in one query (no extra query per card). */
    private static final String SELECT =
            "SELECT l.*, u.full_name AS seller_name, "
            + "(SELECT p.file_path FROM listing_photos p WHERE p.listing_id = l.listing_id ORDER BY p.sort_order LIMIT 1) AS cover_photo, "
            + "r.avg_stars AS seller_rating "
            + "FROM listings l "
            + "JOIN users u ON u.user_id = l.seller_id "
            + "LEFT JOIN (SELECT reviewed_user_id, AVG(stars) AS avg_stars FROM reviews "
            + "           WHERE deleted_by_admin = FALSE GROUP BY reviewed_user_id) r ON r.reviewed_user_id = l.seller_id ";

    public ListingDAO(Database db) {
        super(db);
    }

    public ListingDAO() {
        super();
    }

    @Override
    protected Listing mapRow(ResultSet rs) throws SQLException {
        Listing l = new Listing();
        l.setId(rs.getInt("listing_id"));
        l.setSellerId(rs.getInt("seller_id"));
        l.setTitle(rs.getString("title"));
        l.setCategory(rs.getString("category"));
        l.setPrice(rs.getInt("price"));
        l.setItemCondition(rs.getString("item_condition"));
        l.setCourseCode(rs.getString("course_code"));
        l.setPickupSpot(rs.getString("pickup_spot"));
        l.setDescription(rs.getString("description"));
        l.setStatus(ListingStatus.valueOf(rs.getString("status")));
        l.setBuyerId(intOrNull(rs, "buyer_id"));
        l.setPaymentProofPath(rs.getString("payment_proof_path"));
        l.setDeletedByAdmin(rs.getBoolean("deleted_by_admin"));
        l.setVersion(rs.getInt("version"));
        l.setCreatedAt(time(rs, "created_at"));
        l.setUpdatedAt(time(rs, "updated_at"));
        l.setLastActivityAt(time(rs, "last_activity_at"));
        l.setSellerName(rs.getString("seller_name"));
        l.setCoverPhoto(rs.getString("cover_photo"));
        double rating = rs.getDouble("seller_rating");
        l.setSellerRating(rs.wasNull() ? null : Math.round(rating * 10) / 10.0);
        return l;
    }

    /** Includes listings removed by an administrator; callers decide whether to show them. */
    public Listing findById(int listingId) {
        return queryOne(SELECT + "WHERE l.listing_id = ?", listingId);
    }

    /** Creates an Available listing and returns its id. */
    public int insert(Listing l) {
        return db.inTransaction(c -> insert(c, l));
    }

    /**
     * Creates a listing inside a transaction (e.g. together with its photos) and returns its id.
     * status and version come from the table's defaults: AVAILABLE and 1.
     */
    public int insert(Connection c, Listing l) throws SQLException {
        return insert(c, "INSERT INTO listings (seller_id, title, category, price, item_condition, course_code, pickup_spot, description) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                l.getSellerId(), l.getTitle(), l.getCategory(), l.getPrice(), l.getItemCondition(),
                l.getCourseCode(), l.getPickupSpot(), l.getDescription());
    }

    /** Saves an edit by the seller. Only Available or Inactive listings can be edited. */
    public boolean update(Listing l, int expectedVersion) {
        return update("UPDATE listings SET title = ?, category = ?, price = ?, item_condition = ?, course_code = ?, "
                        + "pickup_spot = ?, description = ?, version = version + 1, last_activity_at = NOW() "
                        + "WHERE listing_id = ? AND seller_id = ? AND version = ? "
                        + "AND status IN ('AVAILABLE', 'INACTIVE') AND deleted_by_admin = FALSE",
                l.getTitle(), l.getCategory(), l.getPrice(), l.getItemCondition(), l.getCourseCode(),
                l.getPickupSpot(), l.getDescription(), l.getId(), l.getSellerId(), expectedVersion) == 1;
    }

    /** Seller changes the status (Available, Pending, Inactive). Returns false if the version or seller does not match. */
    public boolean updateStatus(int listingId, int sellerId, ListingStatus newStatus, int expectedVersion) {
        return update("UPDATE listings SET status = ?, version = version + 1, last_activity_at = NOW() "
                        + "WHERE listing_id = ? AND seller_id = ? AND version = ? AND deleted_by_admin = FALSE",
                newStatus, listingId, sellerId, expectedVersion) == 1;
    }

    /**
     * Mark as Sold, step 1 (inside Database.inTransaction): locks the listing row until the transaction ends,
     * so two Mark as Sold requests for the same listing cannot both succeed.
     */
    public Listing findForUpdate(Connection c, int listingId) throws SQLException {
        return queryOne(c, SELECT + "WHERE l.listing_id = ? FOR UPDATE", listingId);
    }

    /** Mark as Sold, step 2: saves the buyer and the proof of payment. */
    public boolean markSold(Connection c, int listingId, int buyerId, String proofPath, int expectedVersion) throws SQLException {
        return update(c, "UPDATE listings SET status = 'SOLD', buyer_id = ?, payment_proof_path = ?, version = version + 1, "
                        + "last_activity_at = NOW() WHERE listing_id = ? AND version = ? AND status IN ('AVAILABLE', 'PENDING')",
                buyerId, proofPath, listingId, expectedVersion) == 1;
    }

    /** Administrator removes a listing. It stays in the database (soft delete) so reports and reviews still point to it. */
    public boolean deleteByAdmin(Connection c, int listingId) throws SQLException {
        return update(c, "UPDATE listings SET deleted_by_admin = TRUE, version = version + 1 WHERE listing_id = ?", listingId) == 1;
    }

    public boolean deleteByAdmin(int listingId) {
        return update("UPDATE listings SET deleted_by_admin = TRUE, version = version + 1 WHERE listing_id = ?", listingId) == 1;
    }

    /** The Browse feed: 20 listings per page, newest first unless another sort is chosen. */
    public List<Listing> search(SearchQuery q) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE l.deleted_by_admin = FALSE ");
        List<Object> args = new ArrayList<>();

        sql.append(q.showSold ? "AND l.status IN ('AVAILABLE', 'PENDING', 'SOLD') " : "AND l.status IN ('AVAILABLE', 'PENDING') ");
        if (q.keyword != null) {
            sql.append("AND (l.title LIKE ? OR l.description LIKE ? OR l.course_code LIKE ?) ");
            String pattern = likePattern(q.keyword);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (q.category != null) {
            sql.append("AND l.category = ? ");
            args.add(q.category);
        }
        if (q.condition != null) {
            sql.append("AND l.item_condition = ? ");
            args.add(q.condition);
        }
        if (q.minPrice != null) {
            sql.append("AND l.price >= ? ");
            args.add(q.minPrice);
        }
        if (q.maxPrice != null) {
            sql.append("AND l.price <= ? ");
            args.add(q.maxPrice);
        }
        if (q.minRating != null) {
            sql.append("AND r.avg_stars >= ? ");
            args.add(q.minRating);
        }
        if (q.postedWithinDays != null) {
            sql.append("AND l.created_at >= NOW() - INTERVAL ? DAY ");
            args.add(q.postedWithinDays);
        }

        // The sort column is chosen from a fixed list, never copied from the request.
        switch (q.sort) {
            case "price_low" -> sql.append("ORDER BY l.price ASC, l.listing_id DESC ");
            case "price_high" -> sql.append("ORDER BY l.price DESC, l.listing_id DESC ");
            default -> sql.append("ORDER BY l.created_at DESC, l.listing_id DESC ");
        }
        sql.append("LIMIT ? OFFSET ?");
        args.add(SearchQuery.PAGE_SIZE);
        args.add((q.page - 1) * SearchQuery.PAGE_SIZE);

        return queryList(sql.toString(), args.toArray());
    }

    /** A user's profile page. includeHidden is true when sellers look at their own profile. */
    public List<Listing> findBySeller(int sellerId, boolean includeHidden) {
        String visible = includeHidden ? "" : "AND l.status IN ('AVAILABLE', 'PENDING', 'SOLD') ";
        return queryList(SELECT + "WHERE l.seller_id = ? AND l.deleted_by_admin = FALSE " + visible
                + "ORDER BY l.created_at DESC", sellerId);
    }

    /** Available listings with no activity for more than the given number of days (used by the expiry job). */
    public List<Listing> findStale(int days) {
        return queryList(SELECT + "WHERE l.status = 'AVAILABLE' AND l.deleted_by_admin = FALSE "
                + "AND l.last_activity_at < NOW() - INTERVAL ? DAY", days);
    }

    /** Expires one listing if it has not changed since it was found. */
    public boolean markExpired(int listingId, int expectedVersion) {
        return update("UPDATE listings SET status = 'EXPIRED', version = version + 1 "
                + "WHERE listing_id = ? AND version = ? AND status = 'AVAILABLE'", listingId, expectedVersion) == 1;
    }

    /** Records activity (a new message, an edit) so the listing does not expire. */
    public void touch(int listingId) {
        update("UPDATE listings SET last_activity_at = NOW() WHERE listing_id = ?", listingId);
    }
}
