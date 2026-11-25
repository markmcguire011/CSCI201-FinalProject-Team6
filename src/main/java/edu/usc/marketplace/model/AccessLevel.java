package edu.usc.marketplace.model;

/**
 * How much a visitor is allowed to do (section 4), from least to most.
 * The order matters: a higher level can do everything a lower level can.
 */
public enum AccessLevel {
    /** Not logged in. */
    GUEST,
    /** Logged in, but the email is not verified yet. */
    UNVERIFIED,
    /** Logged in with a verified USC email. */
    MEMBER,
    /** An administrator. */
    ADMIN
}
