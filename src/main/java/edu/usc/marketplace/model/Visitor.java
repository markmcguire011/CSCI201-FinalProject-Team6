package edu.usc.marketplace.model;

/**
 * Anyone using the site (section 4): a Guest who is not logged in, a Member, or an Administrator.
 * Each kind of visitor says what its access level is; the rest follows from that.
 */
public abstract class Visitor {

    public abstract AccessLevel getAccessLevel();

    public boolean isLoggedIn() {
        return getAccessLevel() != AccessLevel.GUEST;
    }

    /** True if this visitor's level is at least the level the action needs. */
    public boolean canPerform(Action action) {
        return getAccessLevel().compareTo(action.getMinimumLevel()) >= 0;
    }
}
