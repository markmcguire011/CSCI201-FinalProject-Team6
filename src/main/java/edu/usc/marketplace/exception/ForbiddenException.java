package edu.usc.marketplace.exception;

/** 403: the user is logged in but is not allowed to do this, e.g. editing someone else's listing. */
public class ForbiddenException extends AppException {
    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(403, message);
    }
}
