package edu.usc.marketplace.model;

/** A row of the reviews table. reviewerName is null when the review is anonymous. */
public class Review extends BaseEntity {
    private int listingId;
    private transient int reviewerId;
    private int reviewedUserId;
    private int stars;   // 1 to 5
    private String comment;
    private boolean anonymous;
    private boolean deletedByAdmin;
    private String reviewerName;   // null when anonymous

    public int getListingId() {
        return listingId;
    }

    public void setListingId(int listingId) {
        this.listingId = listingId;
    }

    public int getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(int reviewerId) {
        this.reviewerId = reviewerId;
    }

    public int getReviewedUserId() {
        return reviewedUserId;
    }

    public void setReviewedUserId(int reviewedUserId) {
        this.reviewedUserId = reviewedUserId;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public boolean isDeletedByAdmin() {
        return deletedByAdmin;
    }

    public void setDeletedByAdmin(boolean deletedByAdmin) {
        this.deletedByAdmin = deletedByAdmin;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

}
