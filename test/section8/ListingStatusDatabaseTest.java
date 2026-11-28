package section8;

import edu.usc.marketplace.dao.DataException;
import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.UploadedFile;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import listingsupport.ListingDatabaseFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static edu.usc.marketplace.model.ListingStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MARKETPLACE_TEST_MYSQL_PORT", matches = "[0-9]+")
class ListingStatusDatabaseTest extends ListingDatabaseFixture {
    private final UploadedFile proof = new UploadedFile("receipt.pdf", "%PDF-1.4\nfixture".getBytes(StandardCharsets.US_ASCII));

    private void buyerMessaged(int id) throws Exception {
        int conversation = actions.startConversation(buyer, id);
        execute("INSERT INTO messages(conversation_id,sender_id,body) VALUES(?,?,?)", conversation, buyer, "Interested");
    }

    @Test void staleUpdateAndRepeatedStatusKeepVersionAndEventCount() throws Exception {
        int id = listing("Lamp", 20, AVAILABLE);
        assertEquals(2, actions.change(seller, id, PENDING, 1, null, null).version());
        assertThrows(ConflictException.class, () -> actions.change(seller, id, INACTIVE, 1, null, null));
        assertEquals(2, actions.change(seller, id, PENDING, 2, null, null).version());
        assertEquals(PENDING, listings.findById(id).getStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM listing_events"));
    }

    @Test void firstSaleNeedsMessageAndProofAndReopeningKeepsOneInvitation() throws Exception {
        int id = listing("Desk", 50, AVAILABLE);
        actions.setSaved(buyer, id, true);
        actions.startConversation(buyer, id);
        assertThrows(ValidationException.class, () -> actions.change(seller, id, SOLD, 1, buyer, proof));
        buyerMessaged(id);
        assertThrows(ValidationException.class, () -> actions.change(seller, id, SOLD, 1, buyer, null));
        actions.change(seller, id, SOLD, 1, buyer, proof);
        Listing sold = listings.findById(id);
        assertEquals(buyer, sold.getBuyerId().intValue());
        assertArrayEquals(proof.bytes(), Files.readAllBytes(uploads.resolve("private").resolve(sold.getPaymentProofPath())));
        assertEquals(1, count("SELECT COUNT(*) FROM notifications WHERE type='LISTING_SOLD' AND user_id=2"));
        actions.change(seller, id, AVAILABLE, 2, null, null);
        actions.change(seller, id, SOLD, 3, null, null);
        assertEquals(sold.getPaymentProofPath(), listings.findById(id).getPaymentProofPath());
        assertEquals(1, count("SELECT COUNT(*) FROM payment_proofs"));
        assertEquals(1, count("SELECT COUNT(*) FROM notifications WHERE type='REVIEW_REQUEST' AND user_id=2"));
        assertEquals(1, count("SELECT COUNT(*) FROM listing_events WHERE event_type='BuyerReviewRequested'"));
    }

    @Test void failedEventWriteRollsBackSaleAndDiscardsStagedProof() throws Exception {
        int id = listing("Lamp", 20, AVAILABLE);
        buyerMessaged(id);
        // Reserve the next unique event key to force a failure after the proof and listing writes.
        execute("INSERT INTO listing_events(listing_id,version,event_type) VALUES(?,2,'ListingStatusChanged')", id);
        assertThrows(DataException.class, () -> actions.change(seller, id, SOLD, 1, buyer, proof));
        Listing original = listings.findById(id);
        assertEquals(AVAILABLE, original.getStatus());
        assertEquals(1, original.getVersion());
        assertNull(original.getBuyerId());
        assertNull(original.getPaymentProofPath());
        assertEquals(0, count("SELECT COUNT(*) FROM payment_proofs"));
        assertEquals(0, count("SELECT COUNT(*) FROM notifications"));
        try (var files = Files.list(uploads.resolve("private"))) { assertEquals(0, files.count()); }
    }

    @Test void dailyExpiryIncludesSixtyDayBoundaryAndCanRunTwice() throws Exception {
        int available = listing("Available", 1, AVAILABLE);
        int pending = listing("Pending", 1, PENDING);
        int inactive = listing("Inactive", 1, INACTIVE);
        int sold = listing("Sold", 1, SOLD);
        int recent = listing("Recent", 1, AVAILABLE);
        execute("UPDATE listings SET last_activity_at=NOW()-INTERVAL 60 DAY WHERE listing_id<>?", recent);
        execute("UPDATE listings SET last_activity_at=NOW()-INTERVAL 59 DAY WHERE listing_id=?", recent);
        actions.expireInactiveListings();
        actions.expireInactiveListings();
        for (int id : List.of(available, pending, inactive)) {
            assertEquals(EXPIRED, listings.findById(id).getStatus());
            assertEquals(2, listings.findById(id).getVersion());
        }
        assertEquals(SOLD, listings.findById(sold).getStatus());
        assertEquals(AVAILABLE, listings.findById(recent).getStatus());
        assertEquals(3, count("SELECT COUNT(*) FROM notifications WHERE type='LISTING_EXPIRED' AND user_id=1"));
    }
}
