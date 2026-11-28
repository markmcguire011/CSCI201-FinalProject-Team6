package edu.usc.marketplace.model;

import java.time.LocalDateTime;

/**
 * The small version of a listing that profile pages show (section 6).
 *
 * Only these fields are sent to the browser, never the Listing itself, which has the buyer and the
 * private proof-of-payment file.
 *
 * @param category     the label, e.g. "Textbooks"
 * @param categoryName the enum name stored in the database, e.g. "TEXTBOOKS"
 * @param status       the enum name, e.g. "AVAILABLE"
 * @param coverUrl     "photos/{file name}" of the first photo, or null if the listing has none
 */
public record ListingCard(int id, String title, int price, String category, String categoryName,
                          String status, String coverUrl, LocalDateTime createdAt) {

    public static ListingCard from(Listing l) {
        Category c = Category.fromString(l.getCategory());
        // A value that is not in the Category list (e.g. an old row) is shown as it is stored.
        String label = c == null ? l.getCategory() : c.getLabel();
        String name = c == null ? l.getCategory() : c.name();
        return new ListingCard(l.getId(), l.getTitle(), l.getPrice(), label, name,
                l.getStatus() == null ? null : l.getStatus().name(),
                photoUrl(l.getCoverPhoto()), l.getCreatedAt());
    }

    /** "photos/{file name}" (served by PhotoServlet), or null when there is no photo. */
    public static String photoUrl(String fileName) {
        return fileName == null || fileName.isBlank() ? null : "photos/" + fileName;
    }
}
