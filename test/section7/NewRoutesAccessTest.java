package section7;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.usc.marketplace.model.Action;
import edu.usc.marketplace.service.AccessPolicy;

import org.junit.jupiter.api.Test;

/**
 * White box: the routes added for sections 6 and 7 are covered by AccessPolicy.actionFor, so AuthFilter
 * lets anyone read them and checks the login, the verified email and the suspension before any change.
 */
class NewRoutesAccessTest {

    /** 6/7-R1 */
    @Test
    void profileRoutes() {
        assertEquals(Action.BROWSE, AccessPolicy.actionFor("GET", "/api/profile"));
        assertEquals(Action.EDIT_OWN, AccessPolicy.actionFor("POST", "/api/profile"));
    }

    /** 6/7-R2 */
    @Test
    void listingRoutes() {
        assertEquals(Action.BROWSE, AccessPolicy.actionFor("GET", "/api/listings/31"));
        assertEquals(Action.SELL, AccessPolicy.actionFor("POST", "/api/listings"));
        assertEquals(Action.SELL, AccessPolicy.actionFor("POST", "/api/listings/"));
        assertEquals(Action.EDIT_OWN, AccessPolicy.actionFor("POST", "/api/listings/31"));
    }
}
