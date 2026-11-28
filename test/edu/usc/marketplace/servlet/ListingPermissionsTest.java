package edu.usc.marketplace.servlet;

import edu.usc.marketplace.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ListingPermissionsTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 1, 12, 0);
    private final Listing listing = new Listing();
    private final Member buyer = new Member(2, "Buyer", null, AccountType.STUDENT, true);

    ListingPermissionsTest() {
        listing.setSellerId(1);
        listing.setStatus(ListingStatus.AVAILABLE);
    }

    @ParameterizedTest @EnumSource(ListingStatus.class)
    void availabilityAndExistingSave(ListingStatus status) {
        listing.setStatus(status);
        var p = ListingBrowse.permissions(buyer, listing, false, now);
        assertEquals(status == ListingStatus.AVAILABLE || status == ListingStatus.PENDING, p.canMessage());
        assertEquals(status != ListingStatus.INACTIVE && status != ListingStatus.EXPIRED, p.canSave());
        assertTrue(ListingBrowse.permissions(buyer, listing, true, now).canSave());
        assertFalse(p.canChangeStatus());
    }

    @Test void verificationAndSuspensionApplyEvenToExistingSaves() {
        buyer.setVerified(false);
        var p = ListingBrowse.permissions(buyer, listing, true, now);
        assertFalse(p.canSave()); assertFalse(p.canMessage()); assertTrue(p.canReport());
        buyer.setVerified(true);
        buyer.setSuspendedUntil(now.plusSeconds(1));
        assertFalse(ListingBrowse.permissions(buyer, listing, false, now).canReport());
        assertTrue(ListingBrowse.permissions(buyer, listing, false, now.plusSeconds(1)).canMessage());
    }

    @Test void guestAndSellerActions() {
        var guest = ListingBrowse.permissions(Guest.INSTANCE, listing, false, now);
        assertFalse(guest.canReport()); assertFalse(guest.canMessage()); assertFalse(guest.canSave());
        listing.setSellerId(buyer.getId());
        var owner = ListingBrowse.permissions(buyer, listing, false, now);
        assertTrue(owner.canChangeStatus()); assertFalse(owner.canMessage()); assertFalse(owner.canSave());
    }

    @ParameterizedTest @EnumSource(ListingStatus.class)
    void privateListingsAndModeration(ListingStatus status) {
        listing.setStatus(status);
        assertEquals(status.isPublic(), listing.visibleTo(null));
        assertEquals(status.isPublic(), listing.visibleTo(buyer));
        listing.setSellerId(buyer.getId());
        assertTrue(listing.visibleTo(buyer));
        var admin = new Administrator(3, "Admin", null, AccountType.STUDENT);
        assertTrue(listing.visibleTo(admin));
        listing.setDeletedByAdmin(true);
        assertFalse(listing.visibleTo(admin)); assertFalse(listing.visibleTo(buyer));
    }
}
