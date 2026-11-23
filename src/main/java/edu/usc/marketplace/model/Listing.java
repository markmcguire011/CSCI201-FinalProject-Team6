package edu.usc.marketplace.model;

import java.time.LocalDateTime;
import java.util.List;

/** A row of the listings table, plus the seller's name and cover photo that the feed shows on each card. */
public class Listing extends BaseEntity {
    private int sellerId;
    private String title;
    private String category;
    private int price;   // whole dollars, 0 to 10,000
    private String itemCondition;
    private String courseCode;
    private String pickupSpot;
    private String description;
    private ListingStatus status;
    private Integer buyerId;   // null until sold
    private transient String paymentProofPath;   // private file: only administrators may see it
    private boolean deletedByAdmin;
    private int version;   // raised by 1 on every change (optimistic locking)
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private String sellerName;   // from users, not a listings column
    private String coverPhoto;   // first photo, not a listings column
    private Double sellerRating;   // average stars, null if no reviews
    private List<String> photos;   // all photos in order, filled in by the listing details page

    public int getSellerId() {
        return sellerId;
    }

    public void setSellerId(int sellerId) {
        this.sellerId = sellerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public String getItemCondition() {
        return itemCondition;
    }

    public void setItemCondition(String itemCondition) {
        this.itemCondition = itemCondition;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getPickupSpot() {
        return pickupSpot;
    }

    public void setPickupSpot(String pickupSpot) {
        this.pickupSpot = pickupSpot;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public Integer getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(Integer buyerId) {
        this.buyerId = buyerId;
    }

    public String getPaymentProofPath() {
        return paymentProofPath;
    }

    public void setPaymentProofPath(String paymentProofPath) {
        this.paymentProofPath = paymentProofPath;
    }

    public boolean isDeletedByAdmin() {
        return deletedByAdmin;
    }

    public void setDeletedByAdmin(boolean deletedByAdmin) {
        this.deletedByAdmin = deletedByAdmin;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getCoverPhoto() {
        return coverPhoto;
    }

    public void setCoverPhoto(String coverPhoto) {
        this.coverPhoto = coverPhoto;
    }

    public Double getSellerRating() {
        return sellerRating;
    }

    public void setSellerRating(Double sellerRating) {
        this.sellerRating = sellerRating;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(List<String> photos) {
        this.photos = photos;
    }

}
