package section10;

import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.exception.ForbiddenException;
import edu.usc.marketplace.exception.NotFoundException;
import listingsupport.ListingDatabaseFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static edu.usc.marketplace.model.ListingStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MARKETPLACE_TEST_MYSQL_PORT", matches = "[0-9]+")
class ListingPopupActionsDatabaseTest extends ListingDatabaseFixture {
    @Test void reopeningMessageSellerReusesConversationAndRechecksStatus() throws Exception {
        int id = listing("Lamp", 10, AVAILABLE);
        int first = actions.startConversation(buyer, id);
        assertEquals(first, actions.startConversation(buyer, id));
        assertThrows(ForbiddenException.class, () -> actions.startConversation(seller.getId(), id));
        actions.change(seller, id, INACTIVE, 1, null, null);
        assertThrows(ConflictException.class, () -> actions.startConversation(buyer, id));
        assertEquals(1, count("SELECT COUNT(*) FROM conversations"));
    }

    @Test void repeatedSavePreservesTimestampAndRemovalIsAllowedAfterExpiry() throws Exception {
        int id = listing("Desk", 10, PENDING);
        assertTrue(actions.setSaved(buyer, id, true));
        execute("UPDATE saved_listings SET saved_at='2026-01-01 12:00:00'");
        assertTrue(actions.setSaved(buyer, id, true));
        assertEquals(1, count("SELECT COUNT(*) FROM saved_listings WHERE saved_at='2026-01-01 12:00:00'"));
        execute("UPDATE listings SET last_activity_at=NOW()-INTERVAL 61 DAY");
        actions.expireInactiveListings();
        assertThrows(ConflictException.class, () -> actions.setSaved(buyer, id, true));
        assertFalse(actions.setSaved(buyer, id, false));
        assertFalse(actions.setSaved(buyer, id, false));
        assertEquals(0, count("SELECT COUNT(*) FROM saved_listings"));
    }

    @Test void removedListingsRejectBothActions() throws Exception {
        int id = listing("Removed", 10, AVAILABLE);
        listings.deleteByAdmin(id);
        assertThrows(NotFoundException.class, () -> actions.setSaved(buyer, id, true));
        assertThrows(NotFoundException.class, () -> actions.startConversation(buyer, id));
        assertEquals(0, count("SELECT COUNT(*) FROM saved_listings"));
        assertEquals(0, count("SELECT COUNT(*) FROM conversations"));
    }
}
