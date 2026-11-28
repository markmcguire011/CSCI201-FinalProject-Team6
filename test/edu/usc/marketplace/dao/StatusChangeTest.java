package edu.usc.marketplace.dao;

import edu.usc.marketplace.exception.*;
import edu.usc.marketplace.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatusChangeTest {
    private final Member seller = new Member(1,"Seller",null,AccountType.STUDENT,true);
    private final Listing listing = new Listing();

    StatusChangeTest() { listing.setSellerId(1); listing.setVersion(7); listing.setStatus(ListingStatus.EXPIRED); }

    @Test void expiredOnlyReopensAsAvailable() {
        assertDoesNotThrow(() -> ListingActions.checkChange(seller,listing,ListingStatus.AVAILABLE,7,null,null));
        assertThrows(ValidationException.class, () -> ListingActions.checkChange(seller,listing,ListingStatus.PENDING,7,null,null));
    }
    @Test void staleAndForeignUpdatesCannotChangeAnExpiredListing() {
        assertThrows(ConflictException.class, () -> ListingActions.checkChange(seller,listing,ListingStatus.AVAILABLE,6,null,null));
        listing.setSellerId(2);
        assertThrows(ForbiddenException.class, () -> ListingActions.checkChange(seller,listing,ListingStatus.AVAILABLE,7,null,null));
    }
    @Test void keepsOriginalSaleWhenReopened() {
        listing.setStatus(ListingStatus.SOLD); listing.setBuyerId(2);
        assertDoesNotThrow(() -> ListingActions.checkChange(seller,listing,ListingStatus.AVAILABLE,7,null,null));
        assertThrows(ValidationException.class, () -> ListingActions.checkChange(seller,listing,ListingStatus.SOLD,7,3,null));
        assertThrows(ValidationException.class, () -> ListingActions.checkChange(seller,listing,ListingStatus.SOLD,7,2,new UploadedFile("p.pdf",new byte[10])));
    }
}
