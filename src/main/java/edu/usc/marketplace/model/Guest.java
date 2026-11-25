package edu.usc.marketplace.model;

/**
 * Anyone who is not logged in (section 4). Guests can only browse.
 * Every guest is the same, so there is only one Guest object: Guest.INSTANCE.
 */
public final class Guest extends Visitor {
    public static final Guest INSTANCE = new Guest();

    private Guest() {
    }

    @Override
    public AccessLevel getAccessLevel() {
        return AccessLevel.GUEST;
    }
}
