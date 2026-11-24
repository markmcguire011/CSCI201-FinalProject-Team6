package edu.usc.marketplace.model;

import java.time.LocalDateTime;

/**
 * A logged-in user, as the rest of the app sees them: who they are, whether their email is verified,
 * and whether they are suspended. Built from a row of the users table with Member.from(user).
 */
public class Member {
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
        Member m = new Member(u.getId(), u.getFullName(), u.getEmail(), AccountType.valueOf(u.getAccountType()), u.isVerified());
        m.role = u.isAdmin() ? Role.ADMIN : Role.USER;
        m.suspendedUntil = u.getSuspendedUntil();
        return m;
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
