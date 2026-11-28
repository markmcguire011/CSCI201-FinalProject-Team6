package edu.usc.marketplace.service;

import edu.usc.marketplace.model.Category;
import edu.usc.marketplace.model.EditProfileForm;
import edu.usc.marketplace.model.ItemCondition;
import edu.usc.marketplace.model.ListingForm;
import edu.usc.marketplace.model.RegisterForm;
import edu.usc.marketplace.model.ReportForm;
import edu.usc.marketplace.model.UploadedFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks what users type into forms (sections 3, 6 and 7).
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

    // ----- Profile (section 6) and listings (section 7) -----
    /** The summary above a form whose fields have errors. */
    public static final String FIX_FIELDS = "Fix the fields marked in red and try again.";

    public static final int MAX_BIO = 300;
    public static final int MAX_TITLE = 80;
    public static final int MAX_PRICE = 10_000;
    public static final int MAX_PICKUP_SPOT = 60;
    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_PHOTOS = 5;
    public static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;

    public static final String BIO_ERROR = "Bio must be 300 characters or fewer";
    public static final String PHOTO_SIZE_ERROR = "Photo must be 5 MB or smaller.";
    public static final String PHOTO_TYPE_ERROR = "Photo must be a JPG or PNG file.";
    public static final String TITLE_ERROR = "Enter a title (up to 80 characters)";
    public static final String CATEGORY_ERROR = "Choose a category";
    public static final String PRICE_ERROR = "Price must be a whole number from 0 to 10,000.";
    public static final String CONDITION_ERROR = "Choose a condition";
    public static final String COURSE_CODE_ERROR = "Enter a course code like CSCI 201, or leave it blank";
    public static final String PICKUP_SPOT_ERROR = "Enter a pickup spot (up to 60 characters)";
    public static final String DESCRIPTION_ERROR = "Enter a description (up to 1,000 characters)";
    public static final String TOO_MANY_PHOTOS_ERROR = "You can add up to 5 photos.";

    /** Something before the @, then usc.edu. Checked after trimming and lowercasing. */
    private static final Pattern USC_EMAIL = Pattern.compile("[a-z0-9._%+-]+@usc\\.edu");

    /** Up to five digits. Checked before Integer.parseInt, so a very long number cannot throw. */
    private static final Pattern PRICE = Pattern.compile("^\\d{1,5}$");

    /** 2 to 4 letters, a space, 3 digits, and an optional letter, e.g. "CSCI 201" or "MATH 225A". Checked after normalizeCourseCode. */
    private static final Pattern COURSE_CODE = Pattern.compile("^[A-Z]{2,4} \\d{3}[A-Z]?$");

    private static final byte[] JPG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};

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

    /** Edit Profile page (section 6): full name, optional bio, and an optional new photo. */
    public static Map<String, String> validateProfile(EditProfileForm form) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = trimmed(form.fullName);
        if (name.isEmpty() || name.length() > MAX_NAME) {
            errors.put("fullName", NAME_ERROR);
        }
        if (trimmed(form.bio).length() > MAX_BIO) {
            errors.put("bio", BIO_ERROR);
        }
        if (form.photo != null) {
            String photoError = photoError(form.photo);
            if (photoError != null) errors.put("photo", photoError);
        }
        return errors;
    }

    /**
     * Create and Edit Listing pages (section 7). Every bad field gets its own error. Photos are checked
     * separately by validatePhotos(), because editing also counts the photos being kept.
     */
    public static Map<String, String> validateListing(ListingForm form) {
        Map<String, String> errors = new LinkedHashMap<>();

        String title = trimmed(form.title);
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            errors.put("title", TITLE_ERROR);
        }
        if (Category.fromString(form.category) == null) {
            errors.put("category", CATEGORY_ERROR);
        }
        if (parsePrice(form.price) == null) {
            errors.put("price", PRICE_ERROR);
        }
        if (ItemCondition.fromString(form.condition) == null) {
            errors.put("condition", CONDITION_ERROR);
        }
        String courseCode = normalizeCourseCode(form.courseCode);
        if (courseCode != null && !COURSE_CODE.matcher(courseCode).matches()) {
            errors.put("courseCode", COURSE_CODE_ERROR);
        }
        String pickupSpot = trimmed(form.pickupSpot);
        if (pickupSpot.isEmpty() || pickupSpot.length() > MAX_PICKUP_SPOT) {
            errors.put("pickupSpot", PICKUP_SPOT_ERROR);
        }
        String description = trimmed(form.description);
        if (description.isEmpty() || description.length() > MAX_DESCRIPTION) {
            errors.put("description", DESCRIPTION_ERROR);
        }
        return errors;
    }

    /**
     * Photos for a listing (section 7): at most 5 in all, counting the existing photos being kept,
     * and each new one a JPG or PNG of 5 MB or less. Reports only the first bad photo, with its file name.
     */
    public static Map<String, String> validatePhotos(int keptCount, List<UploadedFile> newPhotos) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<UploadedFile> added = newPhotos == null ? List.of() : newPhotos;

        if (keptCount + added.size() > MAX_PHOTOS) {
            errors.put("photos", TOO_MANY_PHOTOS_ERROR);
            return errors;
        }
        for (UploadedFile file : added) {
            if (file == null) continue;
            String photoError = photoError(file);
            if (photoError != null) {
                errors.put("photos", "\"" + file.fileName() + "\": " + photoError);
                break;
            }
        }
        return errors;
    }

    /**
     * Course codes are stored in one form, so "csci201" and "CSCI  201" are the same course:
     * trimmed, upper case, no spaces except one between the letters and the first digit ("CSCI 201").
     * Null or blank becomes null (no course code).
     */
    public static String normalizeCourseCode(String code) {
        if (code == null || code.isBlank()) return null;
        String compact = code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        for (int i = 1; i < compact.length(); i++) {
            if (Character.isDigit(compact.charAt(i))) {
                return Character.isLetter(compact.charAt(i - 1))
                        ? compact.substring(0, i) + " " + compact.substring(i)
                        : compact;
            }
        }
        return compact;
    }

    /** The price in whole dollars, or null if it is not a whole number from 0 to 10,000. */
    public static Integer parsePrice(String price) {
        String p = trimmed(price);
        if (!PRICE.matcher(p).matches()) return null;
        int value = Integer.parseInt(p);
        return value <= MAX_PRICE ? value : null;
    }

    /** The size or type message for a photo, or null if it is a JPG or PNG of 5 MB or less. */
    private static String photoError(UploadedFile file) {
        if (file.size() > MAX_PHOTO_BYTES) return PHOTO_SIZE_ERROR;
        if (!isJpgOrPng(file.bytes())) return PHOTO_TYPE_ERROR;
        return null;
    }

    /** Checks the first bytes of the file, not its name, so a text file renamed photo.jpg is refused. */
    private static boolean isJpgOrPng(byte[] bytes) {
        return startsWith(bytes, JPG_SIGNATURE) || startsWith(bytes, PNG_SIGNATURE);
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes == null || bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) return false;
        }
        return true;
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    /** Emails are stored trimmed and in lower case, so "TTrojan@USC.EDU" is the same account as "ttrojan@usc.edu". */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
