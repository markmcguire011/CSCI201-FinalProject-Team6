package edu.usc.marketplace.exception;

/** 401: the user must log in first. */
public class AuthException extends AppException {
    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(401, message);
    }
}
