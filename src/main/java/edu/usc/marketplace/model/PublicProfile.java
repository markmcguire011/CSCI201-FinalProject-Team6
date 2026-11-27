package edu.usc.marketplace.model;

import java.util.List;

/**
 * What GET /api/profile sends (section 6): the parts of a user that anyone may see.
 *
 * Built field by field instead of sending the User, which has the password hash and the login counters.
 *
 * @param accountType "Student" or "Alumni"
 * @param joinMonth   e.g. "October 2026"
 * @param photoUrl    "photos/{file name}", or null when the user has no photo
 * @param owner       true when the person viewing is this user
 * @param email       only filled in for the owner (the Edit Profile page shows it read-only); null otherwise
 */
public record PublicProfile(int userId, String fullName, String accountType, String joinMonth, String bio,
                            String photoUrl, boolean owner, String email, List<ListingCard> listings) {
}
