package edu.usc.marketplace.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Create Listing and Edit Listing pages send (section 7). Checked by Validator.validateListing()
 * and Validator.validatePhotos().
 *
 * Every field is the text the user typed, so a bad price like "$40" reaches the validator instead of
 * failing while the request is read.
 */
public class ListingForm {
    public String title;
    public String category;
    public String price;
    public String condition;
    public String courseCode;   // optional
    public String pickupSpot;
    public String description;
    public int version;   // the version the edit page loaded, for optimistic locking; unused when creating
    public List<Integer> keepPhotoIds = new ArrayList<>();   // existing photos to keep, when editing
    public List<UploadedFile> newPhotos = new ArrayList<>();
    /**
     * When editing, the display order of all photos: "keep:<photo id>" or "new:<index into newPhotos>", each kept
     * photo and each new one exactly once. null means the kept photos first, then the new ones.
     */
    public List<String> photoOrder;
}
