package edu.usc.marketplace.service;

import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.EditProfileForm;
import edu.usc.marketplace.model.ListingCard;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.PublicProfile;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.storage.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.IIOException;

/**
 * Viewing and editing a profile (section 6).
 *
 * Editing always uses the logged-in member's id, never an id sent by the browser, so nobody can change
 * someone else's profile.
 */
public class ProfileService {
    private static final Logger LOG = Logger.getLogger(ProfileService.class.getName());

    public static final String NOT_FOUND = "This profile could not be found.";
    public static final String FIX_FIELDS = Validator.FIX_FIELDS;

    private static final DateTimeFormatter JOIN_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final UserDAO users;
    private final ListingDAO listings;
    private final FileStorageService storage;

    /** Used by the app: the shared database pool and upload folder. */
    public ProfileService() {
        this(new UserDAO(), new ListingDAO(), FileStorageService.get());
    }

    /** Used by tests. */
    public ProfileService(UserDAO users, ListingDAO listings, FileStorageService storage) {
        this.users = users;
        this.listings = listings;
        this.storage = storage;
    }

    /**
     * The profile for /api/profile?id=... viewer is the logged-in member, or null for a guest.
     * An id that is missing, not a whole number, or not positive is "not found" without a database lookup.
     */
    public PublicProfile getProfile(Member viewer, String idParam) {
        return build(parseId(idParam), viewer);
    }

    /**
     * Saves the member's name, bio and photo, then returns their refreshed profile.
     *
     *   1. An invalid form changes nothing.
     *   2. A new photo is saved first, so the database never points to a file that does not exist.
     *   3. If the database update fails, the new file is deleted and the old photo is kept.
     *   4. Only after the update succeeds is the old photo file deleted.
     */
    public PublicProfile updateProfile(Member member, EditProfileForm form) {
        Map<String, String> errors = Validator.validateProfile(form);
        if (!errors.isEmpty()) throw new ValidationException(FIX_FIELDS, errors);

        int id = member.getId();
        User user = users.findById(id);
        if (user == null) throw new NotFoundException(NOT_FOUND);
        String oldPath = user.getPhotoPath();

        String savedPath = null;   // the file this request saved, deleted again if the update fails
        String newPath;
        if (form.photo != null) {
            savedPath = savePhoto(form);
            newPath = savedPath;
        } else if (form.removePhoto) {
            newPath = null;
        } else {
            newPath = oldPath;
        }

        String bio = form.bio == null || form.bio.isBlank() ? null : form.bio.trim();
        try {
            users.updateProfile(id, form.fullName.trim(), bio, newPath);
        } catch (RuntimeException e) {
            if (savedPath != null) deleteQuietly(savedPath);
            throw e;
        }

        if (oldPath != null && !oldPath.equals(newPath)) deleteQuietly(oldPath);
        return build(id, member);
    }

    private PublicProfile build(int id, Member viewer) {
        User user = users.findById(id);
        if (user == null) throw new NotFoundException(NOT_FOUND);

        boolean owner = viewer != null && viewer.getId() == id;
        List<ListingCard> cards = listings.findBySeller(id, owner).stream().map(ListingCard::from).toList();

        // TODO section 11 (Reviews): add the average rating, the number of reviews, and the reviews themselves.
        return new PublicProfile(user.getId(), user.getFullName(), accountTypeLabel(user.getAccountType()),
                user.getCreatedAt() == null ? null : JOIN_MONTH.format(user.getCreatedAt()),
                user.getBio(), ListingCard.photoUrl(user.getPhotoPath()), owner,
                owner ? user.getEmail() : null, cards);
    }

    /** The id from the address, or "not found" if it is not a positive whole number. */
    private static int parseId(String idParam) {
        if (idParam == null || idParam.isBlank()) throw new NotFoundException(NOT_FOUND);
        try {
            int id = Integer.parseInt(idParam.trim());
            if (id <= 0) throw new NotFoundException(NOT_FOUND);
            return id;
        } catch (NumberFormatException e) {
            throw new NotFoundException(NOT_FOUND);
        }
    }

    /** "STUDENT" becomes "Student", "ALUMNI" becomes "Alumni". */
    private static String accountTypeLabel(String accountType) {
        if (accountType == null || accountType.isEmpty()) return accountType;
        return accountType.charAt(0) + accountType.substring(1).toLowerCase(Locale.ROOT);
    }

    private String savePhoto(EditProfileForm form) {
        try (InputStream in = form.photo.stream()) {
            return storage.saveImage(form.photo.fileName(), in);
        } catch (ValidationException | IIOException e) {
            // The file starts like a JPG or PNG but is not a picture that can be opened. saveImage reports that as a
            // ValidationException, or as an IIOException when ImageIO fails partway through the file.
            throw new ValidationException(FIX_FIELDS, Map.of("photo", Validator.PHOTO_TYPE_ERROR));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the profile photo", e);
        }
    }

    /** A file that cannot be deleted is only logged: the profile itself was saved. */
    private void deleteQuietly(String fileName) {
        try {
            storage.deletePublic(fileName);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not delete the old photo " + fileName, e);
        }
    }
}
