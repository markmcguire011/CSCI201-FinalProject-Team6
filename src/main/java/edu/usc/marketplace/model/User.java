package edu.usc.marketplace.model;

import java.time.LocalDateTime;

/** A row of the users table. The password hash and login counters are transient, so Gson never sends them to the browser. */
public class User extends BaseEntity {
    private String fullName;
    private String email;
    private transient String passwordHash;   // never sent to the browser
    private String accountType;   // STUDENT or ALUMNI
    private String role;   // USER or ADMIN
    private boolean verified;
    private String bio;
    private String photoPath;
    private transient int failedLoginCount;
    private transient LocalDateTime lockedUntil;
    private LocalDateTime suspendedUntil;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
    }

    public void setSuspendedUntil(LocalDateTime suspendedUntil) {
        this.suspendedUntil = suspendedUntil;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /** Initials for the Profile button in the navigation bar, e.g. "Maya Chen" becomes "MC". */
    public String getInitials() {
        StringBuilder sb = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.toString();
    }

}
