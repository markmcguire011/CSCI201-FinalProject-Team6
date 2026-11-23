package edu.usc.marketplace.exception;

/** 404: the listing, user, photo or conversation does not exist or was removed. */
public class NotFoundException extends AppException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(404, message);
    }
}
