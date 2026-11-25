package section4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.Action;
import edu.usc.marketplace.model.Administrator;
import edu.usc.marketplace.model.Guest;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.service.AccessPolicy;
import edu.usc.marketplace.service.AdminService;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Testing Plan 4c: Checking every request. AccessPolicy.check() holds the checks AuthFilter runs; it
 * returns null when the request is allowed, or the error message.
 */
class CheckingRequestsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);

    private final Member unverified = new Member(2, "Una", "una@usc.edu", AccountType.STUDENT, false);
    private final Member member = new Member(3, "Max", "max@usc.edu", AccountType.ALUMNI, true);
    private final Administrator admin = new Administrator(1, "Ada", "ada@usc.edu", AccountType.STUDENT);

    /** 4c-U1 */
    @Test
    void requestsMapToTheRightAction() {
        assertEquals(Action.BROWSE, AccessPolicy.actionFor("GET", "/api/listings"));
        assertEquals(Action.SAVE, AccessPolicy.actionFor("POST", "/api/saved"));
        assertEquals(Action.SAVE, AccessPolicy.actionFor("GET", "/api/saved"));
        assertEquals(Action.REPORT, AccessPolicy.actionFor("POST", "/api/reports"));
        assertEquals(Action.MESSAGE, AccessPolicy.actionFor("POST", "/api/messages"));
        assertEquals(Action.REVIEW, AccessPolicy.actionFor("POST", "/api/reviews"));
        assertEquals(Action.SELL, AccessPolicy.actionFor("POST", "/api/listings"));
        assertEquals(Action.EDIT_OWN, AccessPolicy.actionFor("PUT", "/api/listings/42"));
        assertEquals(Action.EDIT_OWN, AccessPolicy.actionFor("PUT", "/api/profile"));
        assertEquals(Action.ADMIN, AccessPolicy.actionFor("GET", "/api/admin/reports"));
    }

    /** 4c-U2 */
    @Test
    void unknownRouteIsAdminOnly() {
        assertEquals(Action.ADMIN, AccessPolicy.actionFor("POST", "/api/something-new"));
    }

    /** 4c-U3 */
    @Test
    void guestCanBrowse() {
        assertNull(AccessPolicy.check(Guest.INSTANCE, Action.BROWSE, NOW));
    }

    /** 4c-U4 */
    @Test
    void guestSavingGetsLoginRequired() {
        assertEquals("Login required", AccessPolicy.check(Guest.INSTANCE, Action.SAVE, NOW));
    }

    /** 4c-U5 */
    @Test
    void unverifiedMemberMessagingIsToldToVerify() {
        assertEquals("Verify your USC email to continue",
                AccessPolicy.check(unverified, Action.MESSAGE, NOW));
    }

    /** 4c-U6 */
    @Test
    void unverifiedMemberCanStillReport() {
        assertNull(AccessPolicy.check(unverified, Action.REPORT, NOW));
    }

    /** 4c-U7 */
    @Test
    void memberOpeningAdminPagesIsRefused() {
        assertEquals("You don't have permission to do that", AccessPolicy.check(member, Action.ADMIN, NOW));
    }

    /** 4c-U8 */
    @Test
    void suspendedMemberIsBlockedWithTheEndDate() {
        Member suspended = new Member(4, "Sus", "sus@usc.edu", AccountType.STUDENT, true);
        suspended.setSuspendedUntil(LocalDateTime.of(2026, 11, 3, 0, 0).plusDays(7));
        assertEquals("This account is suspended until Nov 10, 2026.",
                AccessPolicy.check(suspended, Action.SAVE, NOW));
    }

    /** 4c-U9 */
    @Test
    void suspensionThatAlreadyEndedDoesNotBlock() {
        Member was = new Member(5, "Was", "was@usc.edu", AccountType.STUDENT, true);
        was.setSuspendedUntil(NOW.minusDays(1));
        assertNull(AccessPolicy.check(was, Action.SAVE, NOW));
    }

    /** 4c-U10 */
    @Test
    void permanentlySuspendedAdminIsStillBlocked() {
        Administrator a = new Administrator(6, "Bad", "bad@usc.edu", AccountType.ALUMNI);
        a.setSuspendedUntil(AdminService.PERMANENT);
        assertEquals("This account is suspended until Dec 31, 9999.", AccessPolicy.check(a, Action.ADMIN, NOW));
    }

    /** 4c-U11 */
    @Test
    void verifiedMemberAndAdminPassForNormalActions() {
        assertNull(AccessPolicy.check(member, Action.SELL, NOW));
        assertNull(AccessPolicy.check(admin, Action.ADMIN, NOW));
    }
}
