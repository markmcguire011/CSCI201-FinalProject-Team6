package edu.usc.marketplace.model;

/** What the Edit Profile page sends (section 6). Checked by Validator.validateProfile(). */
public class EditProfileForm {
    public String fullName;
    public String bio;
    public UploadedFile photo;   // null when no new photo was chosen
    public boolean removePhoto;

    public EditProfileForm() {
    }

    public EditProfileForm(String fullName, String bio, UploadedFile photo, boolean removePhoto) {
        this.fullName = fullName;
        this.bio = bio;
        this.photo = photo;
        this.removePhoto = removePhoto;
    }
}
