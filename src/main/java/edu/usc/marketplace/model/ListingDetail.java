package edu.usc.marketplace.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What GET /api/listings/{id} sends (section 7): everything the Edit Listing form and the listing pop-up
 * (section 8) show.
 *
 * Built field by field instead of sending the Listing, which has the buyer and the private proof-of-payment file.
 *
 * @param category      the label, e.g. "Textbooks"
 * @param categoryName  the enum name stored in the database, e.g. "TEXTBOOKS"
 * @param condition     the label, e.g. "Like new"
 * @param conditionName the enum name, e.g. "LIKE_NEW"
 * @param status        the enum name, e.g. "AVAILABLE"
 * @param version       send it back when saving an edit, so a change made in the meantime is noticed
 * @param photos        in display order
 * @param owner         true when the person viewing is the seller
 * @param editable      true when the owner may edit it now (Available or Inactive)
 */
public record ListingDetail(int id, int sellerId, String sellerName, String title, String category, String categoryName,
                            int price, String condition, String conditionName, String courseCode, String pickupSpot,
                            String description, String status, int version, List<Photo> photos,
                            LocalDateTime createdAt, LocalDateTime updatedAt, boolean owner, boolean editable) {

    /** One photo: its id (for keepPhotoIds when editing) and its address. */
    public record Photo(int id, String url) {
    }

    /** viewer is the logged-in member, or null for a guest. photos must be in sort order. */
    public static ListingDetail from(Listing l, List<ListingPhoto> photos, Member viewer) {
        Category category = Category.fromString(l.getCategory());
        ItemCondition condition = ItemCondition.fromString(l.getItemCondition());
        boolean owner = viewer != null && viewer.owns(l.getSellerId());
        boolean editable = owner && l.getStatus() != null && l.getStatus().isEditable();
        List<Photo> photoList = photos.stream()
                .map(p -> new Photo(p.getId(), ListingCard.photoUrl(p.getFilePath())))
                .toList();
        // A value that is not in the Category or ItemCondition list (e.g. an old row) is shown as it is stored.
        return new ListingDetail(l.getId(), l.getSellerId(), l.getSellerName(), l.getTitle(),
                category == null ? l.getCategory() : category.getLabel(),
                category == null ? l.getCategory() : category.name(),
                l.getPrice(),
                condition == null ? l.getItemCondition() : condition.getLabel(),
                condition == null ? l.getItemCondition() : condition.name(),
                l.getCourseCode(), l.getPickupSpot(), l.getDescription(),
                l.getStatus() == null ? null : l.getStatus().name(), l.getVersion(), photoList,
                l.getCreatedAt(), l.getUpdatedAt(), owner, editable);
    }
}
