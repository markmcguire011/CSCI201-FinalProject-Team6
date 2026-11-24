package edu.usc.marketplace.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** Reads and writes the verification_codes table: one 6-digit code per user. */
public class VerificationDAO extends BaseDAO<VerificationDAO.Code> {

    /** A row of verification_codes. */
    public record Code(int userId, String code, LocalDateTime expiresAt, LocalDateTime lastSentAt) {
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }

    public VerificationDAO(Database db) {
        super(db);
    }

    public VerificationDAO() {
        super();
    }

    @Override
    protected Code mapRow(ResultSet rs) throws SQLException {
        return new Code(rs.getInt("user_id"), rs.getString("code"), time(rs, "expires_at"), time(rs, "last_sent_at"));
    }

    public Code find(int userId) {
        return queryOne("SELECT * FROM verification_codes WHERE user_id = ?", userId);
    }

    /** Saves a new code, replacing the user's old one. */
    public void save(int userId, String code, LocalDateTime expiresAt) {
        update("INSERT INTO verification_codes (user_id, code, expires_at, last_sent_at) VALUES (?, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE code = VALUES(code), expires_at = VALUES(expires_at), last_sent_at = NOW()",
                userId, code, expiresAt);
    }

    /**
     * Same, but the sent time comes from the caller instead of MySQL's clock, so the expiry and the
     * one-per-minute resend limit are measured on the same clock (section 3).
     */
    public void save(int userId, String code, LocalDateTime expiresAt, LocalDateTime lastSentAt) {
        update("INSERT INTO verification_codes (user_id, code, expires_at, last_sent_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE code = VALUES(code), expires_at = VALUES(expires_at), last_sent_at = VALUES(last_sent_at)",
                userId, code, expiresAt, lastSentAt);
    }

    public void delete(int userId) {
        update("DELETE FROM verification_codes WHERE user_id = ?", userId);
    }
}
