package edu.usc.marketplace.exception;

/** 409: the data changed in the meantime or already exists, e.g. "This listing was just changed. Refresh and try again." */
public class ConflictException extends AppException {
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(409, message);
    }
}
