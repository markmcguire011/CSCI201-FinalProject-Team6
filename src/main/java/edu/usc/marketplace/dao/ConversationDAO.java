package edu.usc.marketplace.dao;

import edu.usc.marketplace.model.Conversation;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes the conversations table: one conversation per buyer per listing. */
public class ConversationDAO extends BaseDAO<Conversation> {

    public ConversationDAO(Database db) {
        super(db);
    }

    public ConversationDAO() {
        super();
    }

    @Override
    protected Conversation mapRow(ResultSet rs) throws SQLException {
        Conversation c = new Conversation();
        c.setId(rs.getInt("conversation_id"));
        c.setListingId(rs.getInt("listing_id"));
        c.setBuyerId(rs.getInt("buyer_id"));
        c.setSellerId(rs.getInt("seller_id"));
        c.setCreatedAt(time(rs, "created_at"));
        c.setLastMessageAt(time(rs, "last_message_at"));
        c.setListingTitle(rs.getString("listing_title"));
        c.setOtherName(rs.getString("other_name"));
        c.setUnreadCount(rs.getInt("unread_count"));
        return c;
    }

    /**
     * Opens the conversation between this buyer and the listing's seller, creating it the first time.
     * INSERT IGNORE plus the UNIQUE (listing_id, buyer_id) rule means two quick clicks still make one row.
     */
    public int findOrCreate(int listingId, int buyerId, int sellerId) {
        update("INSERT IGNORE INTO conversations (listing_id, buyer_id, seller_id) VALUES (?, ?, ?)", listingId, buyerId, sellerId);
        return (int) queryLong("SELECT conversation_id FROM conversations WHERE listing_id = ? AND buyer_id = ?", listingId, buyerId);
    }

    /** One conversation as seen by viewerId (the other person's name, unread count). */
    public Conversation findById(int conversationId, int viewerId) {
        return queryOne(base() + "WHERE c.conversation_id = ?", viewerId, viewerId, viewerId, conversationId);
    }

    /** The inbox: every conversation the user is part of, newest message first. */
    public List<Conversation> findForUser(int userId) {
        return queryList(base() + "WHERE c.buyer_id = ? OR c.seller_id = ? "
                + "ORDER BY COALESCE(c.last_message_at, c.created_at) DESC", userId, userId, userId, userId, userId);
    }

    /** People who messaged the seller about a listing: the choices in the Mark as Sold dialog. */
    public List<Conversation> findBuyersForListing(int listingId, int sellerId) {
        return queryList(base() + "WHERE c.listing_id = ? AND c.seller_id = ? ORDER BY c.last_message_at DESC",
                sellerId, sellerId, sellerId, listingId, sellerId);
    }

    /** Called after a new message is saved, inside the same transaction. */
    public void touch(Connection c, int conversationId) throws SQLException {
        update(c, "UPDATE conversations SET last_message_at = NOW() WHERE conversation_id = ?", conversationId);
    }

    private static String base() {
        // The three ? at the start are the viewer's id: for the other person's name and the unread count.
        return "SELECT c.*, l.title AS listing_title, "
                + "(SELECT full_name FROM users WHERE user_id = IF(c.buyer_id = ?, c.seller_id, c.buyer_id)) AS other_name, "
                + "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.conversation_id "
                + " AND m.is_read = FALSE AND m.sender_id <> ? AND ? IN (c.buyer_id, c.seller_id)) AS unread_count "
                + "FROM conversations c JOIN listings l ON l.listing_id = c.listing_id ";
    }
}
