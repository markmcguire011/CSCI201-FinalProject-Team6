package edu.usc.marketplace.model;

/** The status column of listings. Only AVAILABLE and INACTIVE listings can be edited. */
public enum ListingStatus {
    AVAILABLE, PENDING, SOLD, INACTIVE, EXPIRED;

    public boolean isEditable() {
        return this == AVAILABLE || this == INACTIVE;
    }
}
