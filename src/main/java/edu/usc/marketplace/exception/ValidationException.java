package edu.usc.marketplace.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 400: the request has a missing or invalid value, e.g. "Use your USC email address (ending in usc.edu)." */
public class ValidationException extends AppException {
    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fieldErrors;

    public ValidationException(String message) {
        this(message, Collections.emptyMap());
    }

    /** fieldErrors maps a form field's name to its message, so the page can show it under that field. */
    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(400, message);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
