package edu.usc.marketplace.model;

/**
 * A member with the ADMIN role (section 4). Administrators can do everything a member can, plus
 * the Admin Dashboard. The role is set by the team directly in the database.
 */
public class Administrator extends Member {

    public Administrator(int id, String fullName, String email, AccountType accountType) {
        super(id, fullName, email, accountType, true);
        setRole(Role.ADMIN);
    }

    @Override
    public AccessLevel getAccessLevel() {
        return AccessLevel.ADMIN;
    }
}
