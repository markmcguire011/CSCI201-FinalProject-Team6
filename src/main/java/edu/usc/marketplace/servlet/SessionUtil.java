package edu.usc.marketplace.servlet;

import edu.usc.marketplace.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Starts and reads the login session (sections 3 to 5).
 *
 * The session stores three things: the user's id, their role (USER or ADMIN), and whether their email
 * is verified. The id and role use the attribute names BaseServlet already reads.
 */
public final class SessionUtil {
    public static final String SESSION_VERIFIED = "verified";
    public static final int TIMEOUT_SECONDS = 30 * 60;   // 30 minutes without activity ends the session

    public static final String LOGIN_REQUIRED = "Login required";

    private SessionUtil() {
    }

    /**
     * Logs the user in. Any old session is ended first and a new one is created, so the session id
     * always changes at login.
     */
    public static void startSession(HttpServletRequest req, int userId, String role, boolean verified) {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();

        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(TIMEOUT_SECONDS);
        session.setAttribute(BaseServlet.SESSION_USER_ID, userId);
        session.setAttribute(BaseServlet.SESSION_ROLE, role);
        session.setAttribute(SESSION_VERIFIED, verified);
    }

    /** The logged-in user's id, or null for a guest. */
    public static Integer currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute(BaseServlet.SESSION_USER_ID);
        return id instanceof Integer i ? i : null;
    }

    /** Returns the user's id, or stops the request with 401 "Login required" if nobody is logged in. */
    public static int requireUserId(HttpServletRequest req) {
        Integer id = currentUserId(req);
        if (id == null) throw new AuthException(LOGIN_REQUIRED);
        return id;
    }

    public static boolean isVerified(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_VERIFIED));
    }

    /** Called after the user enters the right code, so they do not have to log in again. */
    public static void markVerified(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) session.setAttribute(SESSION_VERIFIED, true);
    }

    public static void endSession(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
    }
}
