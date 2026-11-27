package section7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.model.Category;
import edu.usc.marketplace.model.ItemCondition;
import edu.usc.marketplace.model.ListingForm;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.service.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testing Plan 7a and 7b: the Create and Edit Listing form. Each test starts from a valid form and changes one field.
 */
class ListingFormTest {
    private static final int FIVE_MB = 5 * 1024 * 1024;
    private static final String PRICE_ERROR = "Price must be a whole number from 0 to 10,000.";

    private static ListingForm validForm() {
        ListingForm form = new ListingForm();
        form.title = "CSCI 201 textbook";
        form.category = "Textbooks";
        form.price = "40";
        form.condition = "Like new";
        form.courseCode = "CSCI 201";
        form.pickupSpot = "Leavey Library entrance";
        form.description = "Barely used, no highlighting.";
        return form;
    }

    /** A file of the given size that starts with the JPG signature. */
    private static UploadedFile jpg(String name, int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return new UploadedFile(name, bytes);
    }

    private static List<UploadedFile> jpgs(int count) {
        List<UploadedFile> files = new ArrayList<>();
        for (int i = 1; i <= count; i++) files.add(jpg("photo" + i + ".jpg", 100));
        return files;
    }

    /** 7a-U1 */
    @Test
    void validFormPasses() {
        assertTrue(Validator.validateListing(validForm()).isEmpty());
    }

    /** 7a-U2 */
    @Test
    void titleLengthBoundary() {
        ListingForm form = validForm();
        form.title = "a".repeat(80);
        assertFalse(Validator.validateListing(form).containsKey("title"));

        form.title = "a".repeat(81);
        assertTrue(Validator.validateListing(form).containsKey("title"));
    }

    /** 7a-U3 */
    @Test
    void spacesOnlyTitleIsRejected() {
        ListingForm form = validForm();
        form.title = "     ";
        assertTrue(Validator.validateListing(form).containsKey("title"));
    }

    /** 7a-U4 */
    @Test
    void categoryMustBeOneOfTheList() {
        ListingForm form = validForm();
        form.category = "Food";
        assertFalse(Validator.validateListing(form).containsKey("category"));

        form.category = "Cars";
        assertTrue(Validator.validateListing(form).containsKey("category"));
    }

    /** 7a-U5 */
    @ParameterizedTest
    @ValueSource(strings = {"0", "10000"})
    void lowestAndHighestPricesAreAllowed(String price) {
        ListingForm form = validForm();
        form.price = price;
        assertFalse(Validator.validateListing(form).containsKey("price"));
    }

    /** 7a-U6 */
    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "10001", "12.50", "$40", "forty", "1e3", "99999999999"})
    void badPricesAreRejected(String price) {
        ListingForm form = validForm();
        form.price = price;
        assertEquals(PRICE_ERROR, Validator.validateListing(form).get("price"));
    }

    /** 7a-U7 */
    @Test
    void conditionMustBeOneOfTheList() {
        ListingForm form = validForm();
        form.condition = "Not applicable";
        assertFalse(Validator.validateListing(form).containsKey("condition"));

        form.condition = "Broken";
        assertTrue(Validator.validateListing(form).containsKey("condition"));
    }

    /** 7a-U8 */
    @Test
    void courseCodeIsOptional() {
        ListingForm form = validForm();
        form.courseCode = "";
        assertFalse(Validator.validateListing(form).containsKey("courseCode"));

        form.courseCode = null;
        assertFalse(Validator.validateListing(form).containsKey("courseCode"));
    }

    /** 7a-U9 */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "csci201        | CSCI 201",
            "'  writ   150 '| WRIT 150",
            "math225a       | MATH 225A",
            "EE 109         | EE 109"
    })
    void courseCodesAreNormalized(String typed, String stored) {
        assertEquals(stored, Validator.normalizeCourseCode(typed));
        ListingForm form = validForm();
        form.courseCode = typed;
        assertFalse(Validator.validateListing(form).containsKey("courseCode"));
    }

    /** 7a-U10 */
    @ParameterizedTest
    @ValueSource(strings = {"CS1", "COMPSCI 201", "201", "CSCI 2010", "C 201"})
    void badCourseCodesAreRejected(String code) {
        ListingForm form = validForm();
        form.courseCode = code;
        assertTrue(Validator.validateListing(form).containsKey("courseCode"));
    }

    /** 7a-U11 */
    @Test
    void pickupSpotLengthBoundary() {
        ListingForm form = validForm();
        form.pickupSpot = "a".repeat(60);
        assertFalse(Validator.validateListing(form).containsKey("pickupSpot"));

        form.pickupSpot = "a".repeat(61);
        assertTrue(Validator.validateListing(form).containsKey("pickupSpot"));

        form.pickupSpot = "";
        assertTrue(Validator.validateListing(form).containsKey("pickupSpot"));
    }

    /** 7a-U12 */
    @Test
    void descriptionLengthBoundary() {
        ListingForm form = validForm();
        form.description = "a".repeat(1000);
        assertFalse(Validator.validateListing(form).containsKey("description"));

        form.description = "a".repeat(1001);
        assertTrue(Validator.validateListing(form).containsKey("description"));

        form.description = "";
        assertTrue(Validator.validateListing(form).containsKey("description"));
    }

    /** 7a-U13 */
    @Test
    void atMostFivePhotosInAll() {
        assertTrue(Validator.validatePhotos(3, jpgs(2)).isEmpty());
        assertTrue(Validator.validatePhotos(0, List.of()).isEmpty());
        assertEquals("You can add up to 5 photos.", Validator.validatePhotos(4, jpgs(2)).get("photos"));
    }

    /** 7a-U14 */
    @Test
    void eachNewPhotoMustBeASmallJpgOrPng() {
        assertTrue(Validator.validatePhotos(0, List.of(jpg("big.jpg", FIVE_MB))).isEmpty());

        assertEquals("\"too-big.jpg\": Photo must be 5 MB or smaller.",
                Validator.validatePhotos(0, List.of(jpg("too-big.jpg", FIVE_MB + 1))).get("photos"));

        // An iPhone HEIC file: a size box, then "ftypheic".
        UploadedFile heic = new UploadedFile("IMG_0001.HEIC",
                new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63});
        assertEquals("\"IMG_0001.HEIC\": Photo must be a JPG or PNG file.",
                Validator.validatePhotos(0, List.of(heic)).get("photos"));
    }

    /** 7a-U15 */
    @Test
    void categoryAndConditionAcceptLabelsInAnyCase() {
        assertEquals(Category.TEXTBOOKS, Category.fromString("Textbooks"));
        assertEquals(Category.TEXTBOOKS, Category.fromString("  tEXTBOOKS "));
        assertEquals(Category.FOOD, Category.fromString("FOOD"));
        assertNull(Category.fromString("Cars"));
        assertNull(Category.fromString(null));

        assertEquals(ItemCondition.LIKE_NEW, ItemCondition.fromString("Like new"));
        assertEquals(ItemCondition.LIKE_NEW, ItemCondition.fromString("LIKE NEW"));
        assertEquals(ItemCondition.LIKE_NEW, ItemCondition.fromString("like_new"));
        assertEquals(ItemCondition.NOT_APPLICABLE, ItemCondition.fromString("not applicable"));
        assertNull(ItemCondition.fromString("Broken"));
        assertNull(ItemCondition.fromString(null));
    }

    /** 7b-U1 */
    @Test
    void emptyFormGivesSixErrorsAndNoneForCourseCode() {
        Map<String, String> errors = Validator.validateListing(new ListingForm());
        assertEquals(6, errors.size());
        assertFalse(errors.containsKey("courseCode"));
    }
}
