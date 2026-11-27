package section7;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.dao.DataException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.ListingForm;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.service.ListingService;
import edu.usc.marketplace.storage.FileStorageService;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testing Plan 7b and 7c: Creating a listing. The DAOs and the transaction are fakes, and photos are
 * saved by a real FileStorageService in a temporary folder. The seller is Sam, user 7.
 */
class CreatingListingTest {
    private final Member sam = new Member(7, "Sam Park", "spark@usc.edu", AccountType.STUDENT, true);

    @TempDir
    Path uploads;

    private FakeListingDAO listings;
    private FakePhotoDAO photos;
    private FakeTransactions transactions;
    private ListingService service;

    @BeforeEach
    void setUp() throws IOException {
        listings = new FakeListingDAO();
        photos = new FakePhotoDAO();
        transactions = new FakeTransactions();
        listings.transactions = transactions;
        photos.transactions = transactions;
        service = new ListingService(listings, photos, new FileStorageService(uploads), transactions);
    }

    private static ListingForm validForm() {
        ListingForm form = new ListingForm();
        form.title = "  CSCI 201 textbook  ";
        form.category = "Textbooks";
        form.price = "40";
        form.condition = "Like new";
        form.courseCode = "csci201";
        form.pickupSpot = " Leavey Library entrance ";
        form.description = " Barely used, no highlighting. ";
        return form;
    }

    /** A small picture that ImageIO can open, as FileStorageService.saveImage requires. */
    private static UploadedFile jpg(String name) throws IOException {
        return jpg(name, 10);
    }

    /** A width-by-width picture. Pictures under 1200 px are saved unchanged, so the bytes identify the file. */
    private static UploadedFile jpg(String name, int width) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, width, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return new UploadedFile(name, out.toByteArray());
    }

    private List<String> savedFiles() throws IOException {
        try (Stream<Path> files = Files.list(uploads.resolve("public"))) {
            return files.map(p -> p.getFileName().toString()).toList();
        }
    }

    /** 7b-U2 */
    @Test
    void invalidFormWritesNothing() throws IOException {
        ListingForm form = validForm();
        form.title = "   ";
        form.price = "12.50";
        form.newPhotos = List.of(jpg("a.jpg"));

        ValidationException e = assertThrows(ValidationException.class, () -> service.create(sam, form));
        assertEquals(List.of("title", "price"), List.copyOf(e.getFieldErrors().keySet()));
        assertEquals(List.of(), savedFiles());
        assertEquals(0, listings.insertCalls);
        assertEquals(0, transactions.calls);
    }

    /** 7c-U1 */
    @Test
    void validFormIsSavedInTheStoredForm() {
        int id = service.create(sam, validForm());

        Listing saved = listings.inserted;
        assertEquals(31, id);
        assertEquals(7, saved.getSellerId());
        assertEquals("CSCI 201 textbook", saved.getTitle());
        assertEquals("TEXTBOOKS", saved.getCategory());
        assertEquals(40, saved.getPrice());
        assertEquals("LIKE_NEW", saved.getItemCondition());
        assertEquals("CSCI 201", saved.getCourseCode());
        assertEquals("Leavey Library entrance", saved.getPickupSpot());
        assertEquals("Barely used, no highlighting.", saved.getDescription());
    }

    /** 7c-U2 */
    @Test
    void photosAreSavedInTheOrderGiven() throws IOException {
        ListingForm form = validForm();
        UploadedFile a = jpg("a.jpg", 10);
        UploadedFile b = jpg("b.jpg", 20);
        form.newPhotos = List.of(a, b);

        service.create(sam, form);
        assertEquals(Integer.valueOf(31), photos.listingId);
        assertEquals(2, photos.filePaths.size());
        Path publicDir = uploads.resolve("public");
        assertArrayEquals(a.bytes(), Files.readAllBytes(publicDir.resolve(photos.filePaths.get(0))));
        assertArrayEquals(b.bytes(), Files.readAllBytes(publicDir.resolve(photos.filePaths.get(1))));
    }

    /** 7c-U3 */
    @Test
    void listingAndPhotosAreSavedInOneTransaction() throws IOException {
        ListingForm form = validForm();
        form.newPhotos = List.of(jpg("a.jpg"));

        service.create(sam, form);
        assertEquals(1, transactions.calls);
        assertTrue(listings.insertedInTransaction);
        assertTrue(photos.insertedInTransaction);
        assertEquals(0, photos.replaceCalls);   // create adds rows with insertAll, never replaceAll
    }

    /** 7c-U4: insertAll fails inside the transaction. */
    @Test
    void failedTransactionDeletesTheSavedFiles() throws IOException {
        ListingForm form = validForm();
        form.newPhotos = List.of(jpg("a.jpg"), jpg("b.jpg"));
        photos.fail = new RuntimeException("The database is down");

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.create(sam, form));
        assertSame(photos.fail, e);
        assertEquals(List.of(), savedFiles());
    }

    /** 7c-W1 */
    @Test
    void secondPhotoThatIsNotARealImageRemovesTheFirst() throws IOException {
        ListingForm form = validForm();
        // Starts like a JPG, so it passes the Validator, but it is not a picture ImageIO can open.
        UploadedFile fake = new UploadedFile("b.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5});
        form.newPhotos = List.of(jpg("a.jpg"), fake);

        ValidationException e = assertThrows(ValidationException.class, () -> service.create(sam, form));
        assertEquals(Map.of("photos", "\"b.jpg\": Photo must be a JPG or PNG file."), e.getFieldErrors());
        assertEquals(List.of(), savedFiles());
        assertEquals(0, listings.insertCalls);
    }

    /** 7c: the seller is always the logged-in member. ListingForm has no seller field to override it. */
    @Test
    void sellerComesFromTheMember() {
        Member other = new Member(12, "Ada", "ada@usc.edu", AccountType.ALUMNI, true);
        service.create(other, validForm());
        assertEquals(12, listings.inserted.getSellerId());
    }

    /** 7c-W2 (white box): a deadlock is retried, and the second try saves the listing with the same files. */
    @Test
    void deadlockIsRetried() throws IOException {
        ListingForm form = validForm();
        form.newPhotos = List.of(jpg("a.jpg"));
        transactions.deadlocks = 1;

        assertEquals(31, service.create(sam, form));
        assertEquals(2, transactions.calls);
        assertEquals(1, savedFiles().size());
        assertEquals(savedFiles(), photos.filePaths);
    }

    /** 7c-W3 (white box): after MAX_ATTEMPTS deadlocks the error reaches the caller and the files are deleted. */
    @Test
    void repeatedDeadlocksGiveUp() throws IOException {
        ListingForm form = validForm();
        form.newPhotos = List.of(jpg("a.jpg"));
        transactions.deadlocks = ListingService.MAX_ATTEMPTS;

        assertThrows(DataException.class, () -> service.create(sam, form));
        assertEquals(ListingService.MAX_ATTEMPTS, transactions.calls);
        assertEquals(List.of(), savedFiles());
    }

    /** 7c-W4 (white box): any other database error is not retried. */
    @Test
    void otherDatabaseErrorsAreNotRetried() {
        transactions.failWith = new DataException(new SQLException("Duplicate entry", "23000", 1062));

        assertThrows(DataException.class, () -> service.create(sam, validForm()));
        assertEquals(1, transactions.calls);
    }
}
