package section4;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.exception.ForbiddenException;
import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.Administrator;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.service.OwnershipGuard;

import org.junit.jupiter.api.Test;

/** Testing Plan 4d: Editing only your own listings and profile. Alex is a verified member with user id 10. */
class EditingOwnTest {
    private final Member alex = new Member(10, "Alex", "alex@usc.edu", AccountType.STUDENT, true);

    /** 4d-U1 */
    @Test
    void editingYourOwnListingIsAllowed() {
        assertTrue(alex.owns(10));
        assertDoesNotThrow(() -> OwnershipGuard.requireOwner(alex, 10));
    }

    /** 4d-U2 */
    @Test
    void editingSomeoneElsesListingIsRefused() {
        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> OwnershipGuard.requireOwner(alex, 11));
        assertEquals("You can only edit your own listings and profile", e.getMessage());
    }

    /** 4d-U3 */
    @Test
    void adminCannotEditAnotherUsersListingEither() {
        Administrator admin = new Administrator(1, "Ada", "ada@usc.edu", AccountType.STUDENT);
        assertThrows(ForbiddenException.class, () -> OwnershipGuard.requireOwner(admin, 10));
    }
}
