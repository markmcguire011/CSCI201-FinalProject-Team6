
package edu.usc.marketplace.service;

import edu.usc.marketplace.model.User;

/**
 * Represents the result of a login attempt.
 * Contains the user on success or an error message on failure.
 */
public class LoginResult {

    private final User user;
    private final int statusCode;
    private final String message;

    private LoginResult(User user, int statusCode, String message) {
        this.user = user;
        this.statusCode = statusCode;
        this.message = message;
    }

    public static LoginResult success(User user) {
        return new LoginResult(user, 200, "Logged in.");
    }

    public static LoginResult failure(int statusCode, String message) {
        return new LoginResult(null, statusCode, message);
    }

    public boolean isSuccess() {
        return user != null;
    }

    public User getUser() {
        return user;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }
}
