package edu.usc.marketplace.dao;

import edu.usc.marketplace.model.Notification;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes the notifications table. */
public class NotificationDAO extends BaseDAO<Notification> {

    public NotificationDAO(Database db) {
        super(db);
    }

    public NotificationDAO() {
        super();
    }

    @Override
    protected Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getInt("notification_id"));
        n.setUserId(rs.getInt("user_id"));
        n.setType(rs.getString("type"));
        n.setText(rs.getString("text"));
        n.setLink(rs.getString("link"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(time(rs, "created_at"));
        return n;
    }

    public int insert(int userId, String type, String text, String link) {
        return insert("INSERT INTO notifications (user_id, type, text, link) VALUES (?, ?, ?, ?)", userId, type, text, link);
    }

    /** Inside a transaction, e.g. the review request created by Mark as Sold. */
    public int insert(Connection c, int userId, String type, String text, String link) throws SQLException {
        return insert(c, "INSERT INTO notifications (user_id, type, text, link) VALUES (?, ?, ?, ?)", userId, type, text, link);
    }

    /** The newest 30 notifications. */
    public List<Notification> findForUser(int userId) {
        return queryList("SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC, notification_id DESC LIMIT 30", userId);
    }

    public int unreadCount(int userId) {
        return (int) queryLong("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE", userId);
    }

    public void markAllRead(int userId) {
        update("UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE", userId);
    }
}
