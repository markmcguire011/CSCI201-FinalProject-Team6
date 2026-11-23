package edu.usc.marketplace.exception;

/**
 * An expected error that the user can fix, such as a wrong field or a missing login.
 * BaseServlet sends it to the browser as {"error": "message"} with the status code below.
 * Each message says how to fix the problem.
 */
public abstract class AppException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;

    protected AppException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
