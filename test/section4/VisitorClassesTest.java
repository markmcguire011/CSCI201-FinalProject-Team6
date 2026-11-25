package section4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.model.AccessLevel;
import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.Action;
import edu.usc.marketplace.model.Administrator;
import edu.usc.marketplace.model.Guest;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.Role;
import edu.usc.marketplace.model.Visitor;

import org.junit.jupiter.api.Test;

/**
 * Testing Plan 4a: Classes. Four visitors: the Guest, an unverified member (Una), a verified member (Max)
 * and an administrator (Ada).
 */
class VisitorClassesTest {
    private final Visitor guest = Guest.INSTANCE;
    private final Member unverified = new Member(2, "Una", "una@usc.edu", AccountType.STUDENT, false);
    private final Member member = new Member(3, "Max", "max@usc.edu", AccountType.ALUMNI, true);
    private final Administrator admin = new Administrator(1, "Ada", "ada@usc.edu", AccountType.STUDENT);

    /** 4a-U1 */
    @Test
    void eachClassReturnsTheRightAccessLevel() {
        assertEquals(AccessLevel.GUEST, guest.getAccessLevel());
        assertEquals(AccessLevel.UNVERIFIED, unverified.getAccessLevel());
        assertEquals(AccessLevel.MEMBER, member.getAccessLevel());
        assertEquals(AccessLevel.ADMIN, admin.getAccessLevel());
    }

    /** 4a-U2 */
    @Test
    void onlyTheGuestIsLoggedOut() {
        assertFalse(guest.isLoggedIn());
        assertTrue(unverified.isLoggedIn());
        assertTrue(member.isLoggedIn());
        assertTrue(admin.isLoggedIn());
    }

    /** 4a-U3 */
    @Test
    void verifyingRaisesTheLevelToMember() {
        Member m = new Member(9, "New", "new@usc.edu", AccountType.STUDENT, false);
        m.setVerified(true);
        assertEquals(AccessLevel.MEMBER, m.getAccessLevel());
    }

    /** 4a-U4 */
    @Test
    void administratorIsAMemberWithTheAdminRole() {
        assertTrue(admin instanceof Member);
        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals(Role.USER, member.getRole());
    }

    /** 4a-U5 */
    @Test
    void guestCanOnlyBrowse() {
        for (Action a : Action.values()) {
            assertEquals(a == Action.BROWSE, guest.canPerform(a), a.name());
        }
    }

    /** 4a-U6 */
    @Test
    void unverifiedMemberCanBrowseAndReportOnly() {
        for (Action a : Action.values()) {
            boolean expected = a == Action.BROWSE || a == Action.REPORT;
            assertEquals(expected, unverified.canPerform(a), a.name());
        }
    }

    /** 4a-U7 */
    @Test
    void verifiedMemberCanDoEverythingExceptAdmin() {
        for (Action a : Action.values()) {
            assertEquals(a != Action.ADMIN, member.canPerform(a), a.name());
        }
    }

    /** 4a-U8 */
    @Test
    void administratorCanDoEverything() {
        for (Action a : Action.values()) {
            assertTrue(admin.canPerform(a), a.name());
        }
    }

    /** 4a-U9 */
    @Test
    void thereIsOnlyOneGuestObject() {
        assertSame(Guest.INSTANCE, guest);
    }
}
