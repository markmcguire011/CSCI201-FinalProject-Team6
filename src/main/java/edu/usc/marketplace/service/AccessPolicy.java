package edu.usc.marketplace.service;

import edu.usc.marketplace.model.AccessLevel;
import edu.usc.marketplace.model.Action;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.Visitor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The rules AuthFilter runs before every /api request (section 4, Checking every request).
 *
 * actionFor() says which action a request is, and check() says whether a visitor may do it.
 *
 * ADDING A NEW /api ROUTE? Add it to actionFor() below. A route that is not listed is treated as
 * administrators-only, so a route nobody planned for is locked by default instead of open by accident.
 */
public final class AccessPolicy {
    public static final String LOGIN_REQUIRED = "Login required";
    public static final String VERIFY_REQUIRED = "Verify your USC email to continue";
    public static final String NO_PERMISSION = "You don't have permission to do that";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private AccessPolicy() {
    }

    /**
     * Which action a request is. path starts after the application name, e.g. "/api/listings/42".
     * Only the first word after /api/ and the HTTP method matter.
     */
    public static Action actionFor(String method, String path) {
        if (method == null || path == null || !path.startsWith("/api/")) return Action.ADMIN;

        String[] parts = path.substring("/api/".length()).split("/");
        String area = parts[0];
        boolean reading = method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("HEAD");
        boolean posting = method.equalsIgnoreCase("POST");

        switch (area) {
            // Open to everyone. The verify and me servlets check the login themselves.
            case "register":
            case "login":
            case "logout":
            case "me":
            case "verify":
                return Action.BROWSE;

            // Reading listings is browsing. POST /api/listings creates one; anything else changes an existing one.
            case "listings":
                if (reading) return Action.BROWSE;
                return posting && parts.length == 1 ? Action.SELL : Action.EDIT_OWN;

            // Anyone can view a profile; changing one is editing your own.
            case "profile":
                return reading ? Action.BROWSE : Action.EDIT_OWN;

            case "saved":
                return Action.SAVE;

            case "messages":
            case "conversations":
                return Action.MESSAGE;

            // Anyone can read reviews; writing one needs a verified email.
            case "reviews":
                return reading ? Action.BROWSE : Action.REVIEW;

            case "reports":
                return posting ? Action.REPORT : Action.ADMIN;

            // Notifications are for any logged-in user, verified or not: the same level as reporting.
            case "notifications":
                return Action.REPORT;

            default:
                return Action.ADMIN;   // includes /api/admin/... and any route not listed above
        }
    }

    /**
     * Returns null when the visitor may do the action, or the message to show when they may not.
     * The checks run in this order: browsing, login, suspension, access level.
     */
    public static String check(Visitor visitor, Action action, LocalDateTime now) {
        if (action == Action.BROWSE) return null;   // browsing is always allowed
        if (!visitor.isLoggedIn()) return LOGIN_REQUIRED;

        if (visitor instanceof Member && ((Member) visitor).isSuspended(now)) {
            return suspendedMessage(((Member) visitor).getSuspendedUntil());
        }
        if (!visitor.canPerform(action)) {
            // Verifying the email only helps for actions a verified member can do.
            boolean verifyingHelps = visitor.getAccessLevel() == AccessLevel.UNVERIFIED
                    && action.getMinimumLevel() == AccessLevel.MEMBER;
            return verifyingHelps ? VERIFY_REQUIRED : NO_PERMISSION;
        }
        return null;
    }

    public static boolean allows(Visitor visitor, Action action) {
        return check(visitor, action, LocalDateTime.now()) == null;
    }

    /** "This account is suspended until Nov 3, 2026." */
    public static String suspendedMessage(LocalDateTime until) {
        return "This account is suspended until " + DATE.format(until) + ".";
    }
}
