package edu.usc.marketplace.model;

/** A row of the listing_photos table: one of up to 5 photos of a listing. */
public class ListingPhoto extends BaseEntity {
    private int listingId;
    private String filePath;   // name of the file in uploads/public
    private int sortOrder;   // 1 to 5

    public int getListingId() {
        return listingId;
    }

    public void setListingId(int listingId) {
        this.listingId = listingId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

}
