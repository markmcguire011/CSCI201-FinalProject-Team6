
package edu.usc.marketplace.service;

import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Handles login validation, password verification,
 * failed login tracking, lockouts, and suspension checks.
 */
public class LoginService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int FAILURE_WINDOW_MINUTES = 10;
    private static final int LOCKOUT_MINUTES = 15;

    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;

    private static final String INVALID_CREDENTIALS =
            "Invalid email or password.";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final UserDAO userDAO;
    private final Clock clock;

    public LoginService() {
        this(new UserDAO(), Clock.systemDefaultZone());
    }

    public LoginService(UserDAO userDAO, Clock clock) {
        this.userDAO = userDAO;
        this.clock = clock;
    }

    /**
     * Authenticates a user.
     *
     * synchronized prevents concurrent requests handled by
     * this service instance from overwriting login counts.
     */
    public synchronized LoginResult login(String email, String password) {

        if (email == null || email.isBlank()
                || password == null || password.isEmpty()) {
            return LoginResult.failure(
                    BAD_REQUEST, "Enter your email and password.");
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        User user = userDAO.findByEmail(normalizedEmail);

        // Never reveal whether an email address exists.
        if (user == null) {
            return LoginResult.failure(
                    UNAUTHORIZED, INVALID_CREDENTIALS);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // Refuse login while the account is locked.
        if (user.getLockedUntil() != null
                && now.isBefore(user.getLockedUntil())) {
            return lockedResult(user.getLockedUntil(), now);
        }

        // Check the password against its stored salted hash.
        if (!PasswordHasher.matches(
                password, user.getPasswordHash())) {

            return recordFailure(user, now);
        }

        // Correct password: clear previous failed attempts.
        userDAO.clearFailures(user.getId());

        // Only reveal suspension after verifying the password.
        LocalDateTime suspendedUntil = user.getSuspendedUntil();

        if (suspendedUntil != null
                && now.isBefore(suspendedUntil)) {

            return LoginResult.failure(
                    FORBIDDEN,
                    "This account is suspended until "
                            + DATE_FORMAT.format(suspendedUntil) + ".");
        }

        // Verified and unverified accounts may both log in.
        return LoginResult.success(user);
    }

    /**
     * Records a failed password attempt and enforces
     * the five-attempt, ten-minute rule.
     */
    private LoginResult recordFailure(User user, LocalDateTime now) {

        LocalDateTime firstFailedAt = user.getFirstFailedAt();
        int count = user.getFailedLoginCount();

        // Begin a new window if the old one has expired.
        if (firstFailedAt == null
                || now.isAfter(
                        firstFailedAt.plusMinutes(FAILURE_WINDOW_MINUTES))) {

            firstFailedAt = now;
            count = 1;

        } else {
            count++;
        }

        // Five failures: lock the account for 15 minutes.
        if (count >= MAX_FAILED_ATTEMPTS) {

            LocalDateTime lockedUntil =
                    now.plusMinutes(LOCKOUT_MINUTES);

            userDAO.saveFailures(
                    user.getId(), 0, null, lockedUntil);

            return lockedResult(lockedUntil, now);
        }

        userDAO.saveFailures(
                user.getId(), count, firstFailedAt, null);

        return LoginResult.failure(
                UNAUTHORIZED, INVALID_CREDENTIALS);
    }

    /**
     * Returns the remaining lockout duration,
     * rounded up to whole minutes.
     */
    private LoginResult lockedResult(
            LocalDateTime lockedUntil, LocalDateTime now) {

        long seconds =
                Duration.between(now, lockedUntil).getSeconds();

        long minutes = Math.max(1, (seconds + 59) / 60);

        return LoginResult.failure(
                TOO_MANY_REQUESTS,
                "Too many failed attempts. Try again in "
                        + minutes + " minutes.");
    }
}

