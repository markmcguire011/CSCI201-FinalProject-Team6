package edu.usc.marketplace.model;

import java.time.LocalDateTime;

/** A row of verification_codes: the one 6-digit code a user can currently use. */
public class VerificationCode {
    private final int userId;
    private final String code;
    private final LocalDateTime expiresAt;
    private final LocalDateTime lastSentAt;

    public VerificationCode(int userId, String code, LocalDateTime expiresAt, LocalDateTime lastSentAt) {
        this.userId = userId;
        this.code = code;
        this.expiresAt = expiresAt;
        this.lastSentAt = lastSentAt;
    }

    public int getUserId() {
        return userId;
    }

    public String getCode() {
        return code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getLastSentAt() {
        return lastSentAt;
    }

    /** A code still works at exactly its expiry time; only later times are rejected. */
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }
}
