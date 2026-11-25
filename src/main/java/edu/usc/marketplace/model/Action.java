package edu.usc.marketplace.model;

/**
 * The things a visitor can try to do (section 4). Each one has the lowest access level allowed to do it:
 * anyone can browse, any logged-in user can report, and the rest need a verified email.
 */
public enum Action {
    BROWSE(AccessLevel.GUEST),
    REPORT(AccessLevel.UNVERIFIED),
    MESSAGE(AccessLevel.MEMBER),
    SAVE(AccessLevel.MEMBER),
    SELL(AccessLevel.MEMBER),
    REVIEW(AccessLevel.MEMBER),
    EDIT_OWN(AccessLevel.MEMBER),
    ADMIN(AccessLevel.ADMIN);

    private final AccessLevel minimumLevel;

    Action(AccessLevel minimumLevel) {
        this.minimumLevel = minimumLevel;
    }

    public AccessLevel getMinimumLevel() {
        return minimumLevel;
    }
}
