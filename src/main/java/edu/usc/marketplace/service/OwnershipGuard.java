package edu.usc.marketplace.service;

import edu.usc.marketplace.exception.ForbiddenException;
import edu.usc.marketplace.model.Member;

/**
 * Users can edit only their own listings and their own profile (section 4).
 *
 * Call it with the owner's id read from the database (the listing's seller_id, or the profile's user_id),
 * never an id sent in the request, so nobody can claim to be the owner:
 *
 *   Listing listing = listings.findById(id);
 *   OwnershipGuard.requireOwner(member, listing.getSellerId());
 *
 * Administrators are not an exception: they remove listings through the Admin Dashboard, not by editing them.
 */
public final class OwnershipGuard {
    public static final String NOT_OWNER = "You can only edit your own listings and profile";

    private OwnershipGuard() {
    }

    /** Stops the request with 403 unless the member is the owner. */
    public static void requireOwner(Member member, int ownerId) {
        if (member == null || !member.owns(ownerId)) throw new ForbiddenException(NOT_OWNER);
    }
}
