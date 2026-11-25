package edu.usc.marketplace.model;

import java.time.LocalDateTime;

/**
 * A logged-in user, as the rest of the app sees them (sections 3 and 4): who they are, whether their
 * email is verified, and whether they are suspended. Built from a row of the users table with
 * Member.from(user), which returns an Administrator when the user has the ADMIN role.
 */
public class Member extends Visitor {
    private final int id;
    private final String fullName;
    private final String email;
    private final AccountType accountType;
    private boolean verified;
    private Role role = Role.USER;
    private LocalDateTime suspendedUntil;

    public Member(int id, String fullName, String email, AccountType accountType, boolean verified) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.accountType = accountType;
        this.verified = verified;
    }

    public static Member from(User u) {
        AccountType type = AccountType.valueOf(u.getAccountType());
        Member m = u.isAdmin()
                ? new Administrator(u.getId(), u.getFullName(), u.getEmail(), type)
                : new Member(u.getId(), u.getFullName(), u.getEmail(), type, u.isVerified());
        m.suspendedUntil = u.getSuspendedUntil();
        return m;
    }

    /** UNVERIFIED until the email is verified, then MEMBER. */
    @Override
    public AccessLevel getAccessLevel() {
        return verified ? AccessLevel.MEMBER : AccessLevel.UNVERIFIED;
    }

    /** True if this member is the owner with the given user id. */
    public boolean owns(int ownerId) {
        return id == ownerId;
    }

    public int getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Role getRole() {
        return role;
    }

    protected void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
    }

    public void setSuspendedUntil(LocalDateTime suspendedUntil) {
        this.suspendedUntil = suspendedUntil;
    }

    /** True until the suspension's end time. At exactly the end time the user is no longer suspended. */
    public boolean isSuspended(LocalDateTime now) {
        return suspendedUntil != null && now.isBefore(suspendedUntil);
    }
}
