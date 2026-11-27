package section6;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.model.EditProfileForm;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.service.Validator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Testing Plan 6b: Editing a profile. Each test starts from a valid form and changes one field.
 */
class EditingProfileTest {
    private static final int FIVE_MB = 5 * 1024 * 1024;

    private static EditProfileForm validForm() {
        return new EditProfileForm("Tommy Trojan", "Selling my old textbooks.", null, false);
    }

    /** A file of the given size that starts with the JPG signature. */
    private static UploadedFile jpg(String name, int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return new UploadedFile(name, bytes);
    }

    private static UploadedFile png(String name) {
        return new UploadedFile(name, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }

    private static UploadedFile textFile(String name) {
        return new UploadedFile(name, "This is not a picture.".getBytes(StandardCharsets.UTF_8));
    }

    /** 6b-U1 */
    @Test
    void shortestAndLongestNameAndBioAreAllowed() {
        EditProfileForm form = validForm();
        form.fullName = "T";
        form.bio = "";
        assertTrue(Validator.validateProfile(form).isEmpty());

        form.fullName = "a".repeat(50);
        form.bio = "b".repeat(300);
        assertTrue(Validator.validateProfile(form).isEmpty());
    }

    /** 6b-U2 */
    @Test
    void blankNameIsRejected() {
        EditProfileForm form = validForm();
        form.fullName = "";
        assertEquals("Enter your full name (up to 50 characters)", Validator.validateProfile(form).get("fullName"));

        form.fullName = "    ";
        assertEquals("Enter your full name (up to 50 characters)", Validator.validateProfile(form).get("fullName"));
    }

    /** 6b-U3 */
    @Test
    void fiftyOneCharacterNameIsRejected() {
        EditProfileForm form = validForm();
        form.fullName = "a".repeat(51);
        assertTrue(Validator.validateProfile(form).containsKey("fullName"));
    }

    /** 6b-U4 */
    @Test
    void threeHundredOneCharacterBioIsRejected() {
        EditProfileForm form = validForm();
        form.bio = "b".repeat(301);
        assertEquals("Bio must be 300 characters or fewer", Validator.validateProfile(form).get("bio"));
    }

    /** 6b-U5 */
    @Test
    void photoSizeBoundary() {
        EditProfileForm form = validForm();
        form.photo = jpg("me.jpg", FIVE_MB);
        assertFalse(Validator.validateProfile(form).containsKey("photo"));

        form.photo = jpg("me.jpg", FIVE_MB + 1);
        assertEquals("Photo must be 5 MB or smaller.", Validator.validateProfile(form).get("photo"));
    }

    /** 6b-U6 */
    @Test
    void textFileNamedLikeAPictureIsRejected() {
        EditProfileForm form = validForm();
        form.photo = textFile("photo.jpg");
        assertEquals("Photo must be a JPG or PNG file.", Validator.validateProfile(form).get("photo"));
    }

    /** 6b-U7 */
    @Test
    void realJpgAndPngAreAccepted() {
        EditProfileForm form = validForm();
        form.photo = jpg("me.jpg", 1024);
        assertTrue(Validator.validateProfile(form).isEmpty());

        form.photo = png("me.png");
        assertTrue(Validator.validateProfile(form).isEmpty());
    }

    /** 6b-U8 */
    @Test
    void everyBadFieldGetsItsOwnError() {
        EditProfileForm form = new EditProfileForm("  ", "b".repeat(301), textFile("photo.png"), false);
        Map<String, String> errors = Validator.validateProfile(form);
        assertEquals(3, errors.size());
        assertTrue(errors.keySet().containsAll(List.of("fullName", "bio", "photo")));
    }

    /** 6b white-box: files shorter than the JPG and PNG signatures are refused without an exception. */
    @Test
    void emptyAndTinyFilesAreRejectedWithoutExceptions() {
        EditProfileForm form = validForm();
        form.photo = new UploadedFile("empty.jpg", new byte[0]);
        Map<String, String> empty = assertDoesNotThrow(() -> Validator.validateProfile(form));
        assertEquals("Photo must be a JPG or PNG file.", empty.get("photo"));

        form.photo = new UploadedFile("tiny.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8});
        Map<String, String> tiny = assertDoesNotThrow(() -> Validator.validateProfile(form));
        assertEquals("Photo must be a JPG or PNG file.", tiny.get("photo"));
    }
}
