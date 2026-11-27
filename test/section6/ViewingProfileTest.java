package section6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.ListingStatus;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.PublicProfile;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.service.ProfileService;
import edu.usc.marketplace.servlet.BaseServlet;
import edu.usc.marketplace.storage.FileStorageService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testing Plan 6a: Viewing a profile. Tommy (user 10) has one listing; Maya (user 11) is another member.
 * The DAOs are fakes, so no database is needed.
 */
class ViewingProfileTest {
    private static final String NOT_FOUND = "This profile could not be found.";

    private final Member tommy = new Member(10, "Tommy Trojan", "ttrojan@usc.edu", AccountType.STUDENT, true);
    private final Member maya = new Member(11, "Maya Chen", "mchen@usc.edu", AccountType.ALUMNI, true);

    private FakeUserDAO users;
    private FakeListingDAO listings;
    private ProfileService service;

    @TempDir
    Path uploads;

    @BeforeEach
    void setUp() throws IOException {
        users = new FakeUserDAO();
        listings = new FakeListingDAO();
        service = new ProfileService(users, listings, new FileStorageService(uploads));

        User u = new User();
        u.setId(10);
        u.setFullName("Tommy Trojan");
        u.setEmail("ttrojan@usc.edu");
        u.setPasswordHash("pbkdf2$secret-hash-value");
        u.setAccountType("STUDENT");
        u.setRole("USER");
        u.setVerified(true);
        u.setBio("Selling my old textbooks.");
        u.setPhotoPath("me.jpg");
        u.setCreatedAt(LocalDateTime.of(2026, 10, 5, 9, 30));
        users.users.put(10, u);

        Listing l = new Listing();
        l.setId(42);
        l.setSellerId(10);
        l.setTitle("CSCI 201 textbook");
        l.setCategory("TEXTBOOKS");
        l.setPrice(40);
        l.setStatus(ListingStatus.SOLD);
        l.setBuyerId(11);
        l.setPaymentProofPath("proof-secret.pdf");
        l.setCoverPhoto("cover.jpg");
        l.setCreatedAt(LocalDateTime.of(2026, 11, 1, 12, 0));
        listings.listings.add(l);
    }

    /** The same Gson BaseServlet sends responses with (it is protected, so a subclass reads it). */
    private static final class Json extends BaseServlet {
        private static final long serialVersionUID = 1L;

        static Gson gson() {
            return GSON;
        }
    }

    /** 6a-U1 */
    @Test
    void ownerSeesTheirOwnProfileWithHiddenListings() {
        PublicProfile p = service.getProfile(tommy, "10");
        assertTrue(p.owner());
        assertEquals(Boolean.TRUE, listings.lastIncludeHidden);
    }

    /** 6a-U2 */
    @Test
    void guestsAndOtherMembersAreNotTheOwner() {
        assertFalse(service.getProfile(null, "10").owner());
        assertEquals(Boolean.FALSE, listings.lastIncludeHidden);

        listings.lastIncludeHidden = null;
        assertFalse(service.getProfile(maya, "10").owner());
        assertEquals(Boolean.FALSE, listings.lastIncludeHidden);
    }

    /** 6a-U3 */
    @Test
    void profileShowsJoinMonthAccountTypeAndEmailOnlyForTheOwner() {
        PublicProfile other = service.getProfile(maya, "10");
        assertEquals("October 2026", other.joinMonth());
        assertEquals("Student", other.accountType());
        assertNull(other.email());

        assertEquals("ttrojan@usc.edu", service.getProfile(tommy, "10").email());
    }

    /** 6a-U4 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "1.5", "7; DROP TABLE users", "0", "-1"})
    void badIdIsNotFoundWithoutADatabaseLookup(String id) {
        NotFoundException e = assertThrows(NotFoundException.class, () -> service.getProfile(maya, id));
        assertEquals(NOT_FOUND, e.getMessage());
        assertEquals(0, users.findByIdCalls);
    }

    /** 6a-U5 */
    @Test
    void unknownIdIsNotFound() {
        NotFoundException e = assertThrows(NotFoundException.class, () -> service.getProfile(maya, "99999"));
        assertEquals(NOT_FOUND, e.getMessage());
    }

    /** 6a-U6 */
    @Test
    void jsonHasNoPrivateFields() {
        String json = Json.gson().toJson(service.getProfile(maya, "10"));
        assertTrue(json.contains("Tommy Trojan"));
        assertTrue(json.contains("\"photoUrl\":\"photos/me.jpg\""));
        assertTrue(json.contains("\"coverUrl\":\"photos/cover.jpg\""));
        assertFalse(json.contains("ttrojan@usc.edu"));
        assertFalse(json.contains("secret-hash-value"));
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("buyerId"));
        assertFalse(json.contains("paymentProofPath"));
        assertFalse(json.contains("proof-secret.pdf"));
    }
}
