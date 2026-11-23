package edu.usc.marketplace.dao;

import java.sql.SQLException;

/**
 * A database error the user cannot fix (MySQL down, a bug in a query).
 * It is unchecked, so BaseServlet catches it and shows "Something went wrong. Please try again."
 */
public class DataException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private static final int MYSQL_DUPLICATE_KEY = 1062;
    private static final int MYSQL_CHECK_FAILED = 3819;

    public DataException(SQLException cause) {
        super(cause.getMessage(), cause);
    }

    public SQLException getSqlException() {
        return (SQLException) getCause();
    }

    /** A UNIQUE rule was broken, e.g. the email already has an account. */
    public boolean isDuplicateKey() {
        return getSqlException().getErrorCode() == MYSQL_DUPLICATE_KEY;
    }

    /** A CHECK rule was broken, e.g. a price over 10,000. */
    public boolean isCheckFailed() {
        return getSqlException().getErrorCode() == MYSQL_CHECK_FAILED;
    }
}
