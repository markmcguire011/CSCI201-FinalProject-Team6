package section7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.exception.ForbiddenException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.ListingDetail;
import edu.usc.marketplace.model.ListingForm;
import edu.usc.marketplace.model.ListingStatus;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.service.ListingService;
import edu.usc.marketplace.service.OwnershipGuard;
import edu.usc.marketplace.servlet.BaseServlet;
import edu.usc.marketplace.storage.FileStorageService;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Testing Plan 7d: Editing a listing. Listing 31 belongs to Sam (user 7), is Available with version 1, and has
 * the photos a.jpg (86), b.jpg (87) and c.jpg (88), which exist as real files in a temporary upload folder.
 * The DAOs and the transaction are fakes.
 */
class EditingListingTest {
    private static final String OUT_OF_DATE = "Photo list is out of date. Refresh and try again.";

    private final Member sam = new Member(7, "Sam Park", "spark@usc.edu", AccountType.STUDENT, true);
    private final Member ada = new Member(12, "Ada Lin", "alin@usc.edu", AccountType.ALUMNI, true);

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

        Listing l = new Listing();
        l.setId(31);
        l.setSellerId(7);
        l.setSellerName("Sam Park");
        l.setTitle("CSCI 201 textbook");
        l.setCategory("TEXTBOOKS");
        l.setPrice(40);
        l.setItemCondition("LIKE_NEW");
        l.setCourseCode("CSCI 201");
        l.setPickupSpot("Leavey Library entrance");
        l.setDescription("Barely used, no highlighting.");
        l.setStatus(ListingStatus.AVAILABLE);
        l.setVersion(1);
        l.setBuyerId(12);
        l.setPaymentProofPath("proof-secret.pdf");
        l.setCreatedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        l.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        listings.stored.put(31, l);

        photos.stored.put(31, new ArrayList<>(List.of(
                FakePhotoDAO.photo(86, 31, "a.jpg", 1),
                FakePhotoDAO.photo(87, 31, "b.jpg", 2),
                FakePhotoDAO.photo(88, 31, "c.jpg", 3))));
        for (String name : List.of("a.jpg", "b.jpg", "c.jpg")) Files.write(publicDir().resolve(name), jpgBytes(10));
    }

    /** An edit of listing 31 that keeps the given photos and changes the price to 30. */
    private static ListingForm form(Integer... keep) {
        ListingForm form = new ListingForm();
        form.title = "CSCI 201 textbook";
        form.category = "Textbooks";
        form.price = "30";
        form.condition = "Like new";
        form.courseCode = "CSCI 201";
        form.pickupSpot = "Leavey Library entrance";
        form.description = "Barely used, no highlighting.";
        form.version = 1;
        form.keepPhotoIds = new ArrayList<>(List.of(keep));
        return form;
    }

    private static byte[] jpgBytes(int width) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, width, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private static UploadedFile jpg(String name) throws IOException {
        return new UploadedFile(name, jpgBytes(12));
    }

    private Path publicDir() {
        return uploads.resolve("public");
    }

    private List<String> files() throws IOException {
        try (Stream<Path> files = Files.list(publicDir())) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** Nothing was written: the three original files only, no UPDATE, no transaction. */
    private void assertNothingWritten() throws IOException {
        assertEquals(List.of("a.jpg", "b.jpg", "c.jpg"), files());
        assertEquals(0, listings.updateCalls);
        assertEquals(0, photos.replaceCalls);
        assertEquals(0, transactions.calls);
    }

    /** The same Gson BaseServlet sends responses with (it is protected, so a subclass reads it). */
    private static final class Json extends BaseServlet {
        private static final long serialVersionUID = 1L;

        static Gson gson() {
            return GSON;
        }
    }

    /** 7d-U1 */
    @Test
    void missingListingIsNotFound() throws IOException {
        NotFoundException e = assertThrows(NotFoundException.class, () -> service.update(sam, 404, form(86)));
        assertEquals("Listing not found.", e.getMessage());
        assertNothingWritten();
    }

    /** 7d-U2 */
    @Test
    void listingRemovedByAdminIsNotFound() throws IOException {
        listings.stored.get(31).setDeletedByAdmin(true);
        ListingForm form = form(86);
        form.newPhotos = List.of(jpg("d.jpg"));

        NotFoundException e = assertThrows(NotFoundException.class, () -> service.update(sam, 31, form));
        assertEquals("Listing not found.", e.getMessage());
        assertNothingWritten();
    }

    /** 7d-U3 */
    @Test
    void anotherMemberCannotEdit() throws IOException {
        ListingForm form = form(86);
        form.newPhotos = List.of(jpg("d.jpg"));

        ForbiddenException e = assertThrows(ForbiddenException.class, () -> service.update(ada, 31, form));
        assertEquals(OwnershipGuard.NOT_OWNER, e.getMessage());
        assertNothingWritten();
    }

    /** 7d-U4 */
    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING", "SOLD", "EXPIRED"})
    void lockedStatusesCannotBeEdited(ListingStatus status) throws IOException {
        listings.stored.get(31).setStatus(status);
        String word = status.name().charAt(0) + status.name().substring(1).toLowerCase();

        ConflictException e = assertThrows(ConflictException.class, () -> service.update(sam, 31, form(86)));
        assertEquals("This listing is " + word + ", so its details are locked.", e.getMessage());
        assertNothingWritten();
    }

    /** 7d-U5 */
    @ParameterizedTest
    @EnumSource(ListingStatus.class)
    void onlyAvailableAndInactiveAreEditable(ListingStatus status) {
        boolean expected = status == ListingStatus.AVAILABLE || status == ListingStatus.INACTIVE;
        assertEquals(expected, status.isEditable());
    }

    /** 7d-U6 */
    @Test
    void keepingAPhotoOfAnotherListingIsRefused() throws IOException {
        ValidationException e = assertThrows(ValidationException.class, () -> service.update(sam, 31, form(86, 500)));
        assertEquals(Map.of("photos", OUT_OF_DATE), e.getFieldErrors());
        assertNothingWritten();
    }

    /** 7d-U7 */
    @Test
    void photosAreSavedInTheNewOrder() throws IOException {
        ListingForm form = form(88, 86);
        form.newPhotos = List.of(jpg("d.jpg"));

        ListingDetail result = service.update(sam, 31, form);
        String d = photos.replacedPaths.get(2);
        assertEquals(List.of("c.jpg", "a.jpg", d), photos.replacedPaths);
        assertEquals(31, photos.replacedListingId);
        assertFalse(List.of("a.jpg", "b.jpg", "c.jpg").contains(d));
        assertEquals(List.of("photos/c.jpg", "photos/a.jpg", "photos/" + d),
                result.photos().stream().map(ListingDetail.Photo::url).toList());
        assertEquals(30, result.price());
        assertEquals(2, result.version());
        assertEquals(1, listings.updatedExpectedVersion);
        assertTrue(listings.updatedInTransaction && photos.replacedInTransaction);
        assertEquals(1, transactions.calls);
    }

    /** 7d-U8 */
    @Test
    void droppedPhotoFileIsDeletedOnlyAfterCommit() throws IOException {
        ListingForm form = form(88, 86);
        form.newPhotos = List.of(jpg("d.jpg"));
        service.update(sam, 31, form);
        assertFalse(Files.exists(publicDir().resolve("b.jpg")));
        assertTrue(Files.exists(publicDir().resolve("a.jpg")));
        assertTrue(Files.exists(publicDir().resolve("c.jpg")));
    }

    /** 7d-U8: with a failing replaceAll the dropped photo is kept and the new file removed. */
    @Test
    void failedSaveKeepsEveryExistingFile() throws IOException {
        photos.failReplace = new RuntimeException("The database is down");
        ListingForm form = form(88, 86);
        form.newPhotos = List.of(jpg("d.jpg"));

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.update(sam, 31, form));
        assertSame(photos.failReplace, e);
        assertEquals(List.of("a.jpg", "b.jpg", "c.jpg"), files());
    }

    /** 7d-U10 */
    @Test
    void staleVersionIsAConflictAndRemovesTheNewFile() throws IOException {
        listings.updateResult = false;
        ListingForm form = form(88, 86);
        form.newPhotos = List.of(jpg("d.jpg"));

        ConflictException e = assertThrows(ConflictException.class, () -> service.update(sam, 31, form));
        assertEquals("This listing was just changed. Refresh and try again.", e.getMessage());
        assertEquals(List.of("a.jpg", "b.jpg", "c.jpg"), files());
        assertEquals(0, photos.replaceCalls);
    }

    // ----- 7d-W1 (white box): every early exit, steps a to e, writes nothing and starts no transaction -----

    /** 7d-W1 a: missing listing. */
    @Test
    void earlyExitMissing() throws IOException {
        ListingForm form = form(86);
        form.newPhotos = List.of(jpg("d.jpg"));
        assertThrows(NotFoundException.class, () -> service.update(sam, 404, form));
        assertNothingWritten();
    }

    /** 7d-W1 b: not the owner. */
    @Test
    void earlyExitNotOwner() throws IOException {
        ListingForm form = form(86);
        form.newPhotos = List.of(jpg("d.jpg"));
        assertThrows(ForbiddenException.class, () -> service.update(ada, 31, form));
        assertNothingWritten();
    }

    /** 7d-W1 c: locked status. */
    @Test
    void earlyExitLocked() throws IOException {
        listings.stored.get(31).setStatus(ListingStatus.SOLD);
        ListingForm form = form(86);
        form.newPhotos = List.of(jpg("d.jpg"));
        assertThrows(ConflictException.class, () -> service.update(sam, 31, form));
        assertNothingWritten();
    }

    /** 7d-W1 d: a kept photo listed twice. */
    @Test
    void earlyExitDuplicateKeptPhoto() throws IOException {
        ListingForm form = form(86, 86);
        form.newPhotos = List.of(jpg("d.jpg"));
        ValidationException e = assertThrows(ValidationException.class, () -> service.update(sam, 31, form));
        assertEquals(Map.of("photos", OUT_OF_DATE), e.getFieldErrors());
        assertNothingWritten();
    }

    /** 7d-W1 e: a bad field, and too many photos (3 kept + 3 new). */
    @Test
    void earlyExitInvalidForm() throws IOException {
        ListingForm badPrice = form(86);
        badPrice.price = "12.50";
        badPrice.newPhotos = List.of(jpg("d.jpg"));
        ValidationException e = assertThrows(ValidationException.class, () -> service.update(sam, 31, badPrice));
        assertEquals(List.of("price"), List.copyOf(e.getFieldErrors().keySet()));
        assertNothingWritten();

        ListingForm tooMany = form(86, 87, 88);
        tooMany.newPhotos = List.of(jpg("d.jpg"), jpg("e.jpg"), jpg("f.jpg"));
        ValidationException e2 = assertThrows(ValidationException.class, () -> service.update(sam, 31, tooMany));
        assertEquals(Map.of("photos", "You can add up to 5 photos."), e2.getFieldErrors());
        assertNothingWritten();
    }

    // ----- GET /api/listings/{id} -----

    /** 7d: the owner may edit; anyone else, or a guest, sees the listing but may not. */
    @Test
    void getListingTellsWhoMayEdit() {
        ListingDetail mine = service.getListing(sam, 31);
        assertTrue(mine.owner());
        assertTrue(mine.editable());
        assertEquals("Textbooks", mine.category());
        assertEquals("LIKE_NEW", mine.conditionName());
        assertEquals("Like new", mine.condition());
        assertEquals(List.of(86, 87, 88), mine.photos().stream().map(ListingDetail.Photo::id).toList());

        ListingDetail theirs = service.getListing(ada, 31);
        assertFalse(theirs.owner());
        assertFalse(theirs.editable());
        assertFalse(service.getListing(null, 31).owner());

        listings.stored.get(31).setStatus(ListingStatus.PENDING);
        assertFalse(service.getListing(sam, 31).editable());   // the owner, but locked
    }

    /** 7d: the JSON has no buyer or proof of payment, and a listing removed by an admin is not found. */
    @Test
    void getListingJsonHasNoPrivateFields() {
        String json = Json.gson().toJson(service.getListing(ada, 31));
        assertTrue(json.contains("\"title\":\"CSCI 201 textbook\""));
        assertTrue(json.contains("\"url\":\"photos/a.jpg\""));
        assertFalse(json.contains("buyerId"));
        assertFalse(json.contains("paymentProofPath"));
        assertFalse(json.contains("proof-secret.pdf"));

        listings.stored.get(31).setDeletedByAdmin(true);
        NotFoundException e = assertThrows(NotFoundException.class, () -> service.getListing(sam, 31));
        assertEquals("Listing not found.", e.getMessage());
    }
}
