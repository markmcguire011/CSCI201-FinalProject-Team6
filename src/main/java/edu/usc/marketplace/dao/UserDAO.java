package edu.usc.marketplace.dao;

import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.model.User;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** Reads and writes the users table. */
public class UserDAO extends BaseDAO<User> {

    public UserDAO(Database db) {
        super(db);
    }

    public UserDAO() {
        super();
    }

    @Override
    protected User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("user_id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setAccountType(rs.getString("account_type"));
        u.setRole(rs.getString("role"));
        u.setVerified(rs.getBoolean("verified"));
        u.setBio(rs.getString("bio"));
        u.setPhotoPath(rs.getString("photo_path"));
        u.setFailedLoginCount(rs.getInt("failed_login_count"));
        u.setLockedUntil(time(rs, "locked_until"));
        u.setSuspendedUntil(time(rs, "suspended_until"));
        u.setCreatedAt(time(rs, "created_at"));
        return u;
    }

    public User findById(int userId) {
        return queryOne("SELECT * FROM users WHERE user_id = ?", userId);
    }

    /** Emails are stored in lower case, so "MChen@USC.edu" finds the same account. */
    public User findByEmail(String email) {
        return queryOne("SELECT * FROM users WHERE email = ?", email.trim().toLowerCase());
    }

    /** True if an account already uses this email (section 3, Registering). */
    public boolean emailExists(String email) {
        return queryLong("SELECT COUNT(*) FROM users WHERE email = ?", email.trim().toLowerCase()) > 0;
    }

    /** Creates an unverified account and returns its id. */
    public int insert(User u) {
        try {
            return insert("INSERT INTO users (full_name, email, password_hash, account_type) VALUES (?, ?, ?, ?)",
                    u.getFullName(), u.getEmail().trim().toLowerCase(), u.getPasswordHash(), u.getAccountType());
        } catch (DataException e) {
            if (e.isDuplicateKey()) throw new ConflictException("An account with this email already exists. Log in instead.");
            throw e;
        }
    }

    public void setVerified(int userId) {
        update("UPDATE users SET verified = TRUE WHERE user_id = ?", userId);
    }

    public void updateProfile(int userId, String fullName, String bio, String photoPath) {
        update("UPDATE users SET full_name = ?, bio = ?, photo_path = ? WHERE user_id = ?", fullName, bio, photoPath, userId);
    }

    public void updatePassword(int userId, String passwordHash) {
        update("UPDATE users SET password_hash = ? WHERE user_id = ?", passwordHash, userId);
    }

    /** Adds one failed login and returns the new count. */
    public int recordFailedLogin(int userId) {
        update("UPDATE users SET failed_login_count = failed_login_count + 1 WHERE user_id = ?", userId);
        return (int) queryLong("SELECT failed_login_count FROM users WHERE user_id = ?", userId);
    }

    public void resetFailedLogins(int userId) {
        update("UPDATE users SET failed_login_count = 0, locked_until = NULL WHERE user_id = ?", userId);
    }

    public void lockUntil(int userId, LocalDateTime until) {
        update("UPDATE users SET locked_until = ? WHERE user_id = ?", until, userId);
    }

    /** null removes the suspension. */
    public void suspendUntil(int userId, LocalDateTime until) {
        update("UPDATE users SET suspended_until = ? WHERE user_id = ?", until, userId);
    }

    /** Inside a transaction, e.g. resolving a report (section 3). */
    public void suspendUntil(Connection c, int userId, LocalDateTime until) throws SQLException {
        update(c, "UPDATE users SET suspended_until = ? WHERE user_id = ?", until, userId);
    }
}
