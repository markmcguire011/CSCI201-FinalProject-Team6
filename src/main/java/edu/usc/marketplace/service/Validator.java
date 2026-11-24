package edu.usc.marketplace.service;

import edu.usc.marketplace.model.RegisterForm;
import edu.usc.marketplace.model.ReportForm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks what users type into forms (section 3).
 *
 * Each method returns one error message per bad field, keyed by the field's name, so the page can show
 * each message under its own field. An empty map means the form is valid.
 */
public final class Validator {
    public static final int MAX_NAME = 50;
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_REPORT_COMMENT = 300;

    public static final String NAME_ERROR = "Enter your full name (up to 50 characters)";
    public static final String EMAIL_ERROR = "Use your USC email address";
    public static final String PASSWORD_ERROR = "Password must be at least 8 characters";
    public static final String CONFIRM_ERROR = "Passwords do not match";
    public static final String ACCOUNT_TYPE_ERROR = "Choose Student or Alumni";
    public static final String REASON_ERROR = "Pick a reason";
    public static final String COMMENT_ERROR = "Comment must be 300 characters or fewer";
    public static final String TARGET_ERROR = "Choose a listing or user to report";

    /** Something before the @, then usc.edu. Checked after trimming and lowercasing. */
    private static final Pattern USC_EMAIL = Pattern.compile("[a-z0-9._%+-]+@usc\\.edu");

    private Validator() {
    }

    /** Register page: full name, USC email, password, confirm password, Student or Alumni. */
    public static Map<String, String> validateRegistration(RegisterForm form) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = form.fullName == null ? "" : form.fullName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            errors.put("fullName", NAME_ERROR);
        }
        if (form.email == null || !USC_EMAIL.matcher(normalizeEmail(form.email)).matches()) {
            errors.put("email", EMAIL_ERROR);
        }
        if (form.password == null || form.password.length() < MIN_PASSWORD) {
            errors.put("password", PASSWORD_ERROR);
        }
        if (form.password == null || !form.password.equals(form.confirmPassword)) {
            errors.put("confirmPassword", CONFIRM_ERROR);
        }
        if (form.accountType == null) {
            errors.put("accountType", ACCOUNT_TYPE_ERROR);
        }
        return errors;
    }

    /** Report dialog: one of the four reasons, and an optional comment of up to 300 characters. */
    public static Map<String, String> validateReport(ReportForm form) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (form.targetType == null || form.targetId <= 0) {
            errors.put("target", TARGET_ERROR);
        }
        if (form.reason == null) {
            errors.put("reason", REASON_ERROR);
        }
        if (form.comment != null && form.comment.length() > MAX_REPORT_COMMENT) {
            errors.put("comment", COMMENT_ERROR);
        }
        return errors;
    }

    /** Emails are stored trimmed and in lower case, so "TTrojan@USC.EDU" is the same account as "ttrojan@usc.edu". */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
