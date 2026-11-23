package edu.usc.marketplace.dao;

import edu.usc.marketplace.model.Message;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes the messages table. */
public class MessageDAO extends BaseDAO<Message> {
    public static final int HISTORY_PAGE = 50;

    public MessageDAO(Database db) {
        super(db);
    }

    public MessageDAO() {
        super();
    }

    @Override
    protected Message mapRow(ResultSet rs) throws SQLException {
        Message m = new Message();
        m.setId(rs.getInt("message_id"));
        m.setConversationId(rs.getInt("conversation_id"));
        m.setSenderId(rs.getInt("sender_id"));
        m.setBody(rs.getString("body"));
        m.setSentAt(time(rs, "sent_at"));
        m.setCreatedAt(m.getSentAt());
        m.setRead(rs.getBoolean("is_read"));
        return m;
    }

    /** Saves a message and updates the conversation's last_message_at in one transaction. Returns the saved message. */
    public Message send(int conversationId, int senderId, String body) {
        return db.inTransaction(c -> {
            int id = insert(c, conversationId, senderId, body);
            new ConversationDAO(db).touch(c, conversationId);
            return queryOne(c, "SELECT * FROM messages WHERE message_id = ?", id);
        });
    }

    public int insert(Connection c, int conversationId, int senderId, String body) throws SQLException {
        return insert(c, "INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, ?)", conversationId, senderId, body);
    }

    /**
     * Chat history, oldest first. Loads the newest 50; pass the id of the oldest message shown as beforeId
     * to load the 50 before it. Uses the (conversation_id, sent_at) index.
     */
    public List<Message> history(int conversationId, Integer beforeId) {
        List<Message> newestFirst = beforeId == null
                ? queryList("SELECT * FROM messages WHERE conversation_id = ? ORDER BY sent_at DESC, message_id DESC LIMIT ?",
                        conversationId, HISTORY_PAGE)
                : queryList("SELECT * FROM messages WHERE conversation_id = ? AND message_id < ? "
                        + "ORDER BY sent_at DESC, message_id DESC LIMIT ?", conversationId, beforeId, HISTORY_PAGE);
        java.util.Collections.reverse(newestFirst);
        return newestFirst;
    }

    /** Marks the other person's messages as read when the reader opens the conversation. */
    public void markRead(int conversationId, int readerId) {
        update("UPDATE messages SET is_read = TRUE WHERE conversation_id = ? AND sender_id <> ? AND is_read = FALSE",
                conversationId, readerId);
    }

    /** The number next to Messages in the navigation bar. */
    public int unreadCount(int userId) {
        return (int) queryLong("SELECT COUNT(*) FROM messages m JOIN conversations c ON c.conversation_id = m.conversation_id "
                + "WHERE (c.buyer_id = ? OR c.seller_id = ?) AND m.sender_id <> ? AND m.is_read = FALSE", userId, userId, userId);
    }
}
