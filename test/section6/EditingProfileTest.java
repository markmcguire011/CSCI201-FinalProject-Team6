package section6;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.EditProfileForm;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.service.ProfileService;
import edu.usc.marketplace.service.Validator;
import edu.usc.marketplace.storage.FileStorageService;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testing Plan 6b: Editing a profile. Each test starts from a valid form and changes one field.
 * Tests U9 to U13 save through ProfileService with a fake UserDAO and a real FileStorageService in a temporary folder.
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

    /** 6b-W1 (white box): files shorter than the JPG and PNG signatures are refused without an exception. */
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

    // ----- saving through ProfileService (6b-U9 to U13) -----

    @TempDir
    Path uploads;

    private final Member tommy = new Member(10, "Tommy Trojan", "ttrojan@usc.edu", AccountType.STUDENT, true);

    /** A service whose user 10 has the photo old.jpg, which exists in the public upload folder. */
    private ProfileService serviceWithOldPhoto(FakeUserDAO users) throws IOException {
        FileStorageService storage = new FileStorageService(uploads);
        Files.write(publicDir().resolve("old.jpg"), realJpg());

        User u = new User();
        u.setId(10);
        u.setFullName("Tommy Trojan");
        u.setEmail("ttrojan@usc.edu");
        u.setAccountType("STUDENT");
        u.setPhotoPath("old.jpg");
        u.setCreatedAt(LocalDateTime.of(2026, 10, 5, 9, 30));
        users.users.put(10, u);
        return new ProfileService(users, new FakeListingDAO(), storage);
    }

    private Path publicDir() {
        return uploads.resolve("public");
    }

    private List<String> publicFiles() throws IOException {
        try (Stream<Path> files = Files.list(publicDir())) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** A small picture that ImageIO can open, as FileStorageService.saveImage requires. */
    private static byte[] realJpg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    /** 6b-U9 */
    @Test
    void nameAndBioAreTrimmedAndPhotoIsKept() throws IOException {
        FakeUserDAO users = new FakeUserDAO();
        ProfileService service = serviceWithOldPhoto(users);

        service.updateProfile(tommy, new EditProfileForm("  Tommy T.  ", "  New bio ", null, false));
        assertEquals("Tommy T.", users.savedName);
        assertEquals("New bio", users.savedBio);
        assertEquals("old.jpg", users.savedPhotoPath);
        assertEquals(List.of("old.jpg"), publicFiles());

        service.updateProfile(tommy, new EditProfileForm("Tommy T.", "   ", null, false));
        assertNull(users.savedBio);   // a blank bio is stored as no bio
    }

    /** 6b-U10 */
    @Test
    void newPhotoIsSavedAndOldOneDeleted() throws IOException {
        FakeUserDAO users = new FakeUserDAO();
        ProfileService service = serviceWithOldPhoto(users);

        service.updateProfile(tommy, new EditProfileForm("Tommy", null, new UploadedFile("me.jpg", realJpg()), false));
        String saved = users.savedPhotoPath;
        assertTrue(saved.endsWith(".jpg") && !saved.equals("old.jpg"));
        assertTrue(Files.exists(publicDir().resolve(saved)));
        assertFalse(Files.exists(publicDir().resolve("old.jpg")));
    }

    /** 6b-U11 */
    @Test
    void failedUpdateKeepsOldPhotoAndRemovesNewOne() throws IOException {
        FakeUserDAO users = new FakeUserDAO();
        ProfileService service = serviceWithOldPhoto(users);
        users.failUpdate = new RuntimeException("The database is down");

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.updateProfile(tommy,
                new EditProfileForm("Tommy", null, new UploadedFile("me.jpg", realJpg()), false)));
        assertSame(users.failUpdate, e);
        assertEquals(List.of("old.jpg"), publicFiles());
    }

    /** 6b-U12 */
    @Test
    void invalidFormChangesNothing() throws IOException {
        FakeUserDAO users = new FakeUserDAO();
        ProfileService service = serviceWithOldPhoto(users);

        ValidationException e = assertThrows(ValidationException.class, () -> service.updateProfile(tommy,
                new EditProfileForm("  ", null, new UploadedFile("me.jpg", realJpg()), false)));
        assertTrue(e.getFieldErrors().containsKey("fullName"));
        assertEquals(0, users.updateCalls);
        assertEquals(List.of("old.jpg"), publicFiles());
    }

    /** 6b-U13 */
    @Test
    void removingThePhotoStoresNullAndDeletesTheFile() throws IOException {
        FakeUserDAO users = new FakeUserDAO();
        ProfileService service = serviceWithOldPhoto(users);

        service.updateProfile(tommy, new EditProfileForm("Tommy", null, null, true));
        assertEquals(1, users.updateCalls);
        assertNull(users.savedPhotoPath);
        assertEquals(List.of(), publicFiles());
    }

    /** 6b-W2 (white box): a file that starts like a JPG but is not a picture is a 400 on the photo, and nothing is saved. */
    @Test
    void brokenJpgIsRejectedAsNotAPicture() throws IOException {
        FakeUserDAO users = new FakeUserDAO();
        ProfileService service = serviceWithOldPhoto(users);
        UploadedFile broken = new UploadedFile("me.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5});

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.updateProfile(tommy, new EditProfileForm("Tommy", null, broken, false)));
        assertEquals(Map.of("photo", "Photo must be a JPG or PNG file."), e.getFieldErrors());
        assertEquals(0, users.updateCalls);
        assertEquals(List.of("old.jpg"), publicFiles());
    }

    /** 6b-W3 (white box): two saves with new photos at the same moment leave exactly one photo file, the stored one. */
    @Test
    void overlappingSavesLeaveNoUnusedFile() throws Exception {
        FakeUserDAO users = new FakeUserDAO();
        ProfileService service = serviceWithOldPhoto(users);
        users.updateDelayMillis = 200;   // without the per-user lock, both saves read old.jpg before either updates

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<?>> saves = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                saves.add(pool.submit(() -> {
                    start.await();
                    return service.updateProfile(tommy, new EditProfileForm("Tommy", null, new UploadedFile("me.jpg", realJpg()), false));
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> f : saves) f.get();
        } finally {
            pool.shutdown();
        }
        assertEquals(2, users.updateCalls);
        assertEquals(List.of(users.savedPhotoPath), publicFiles());
    }
}
