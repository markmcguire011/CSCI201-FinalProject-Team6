package edu.usc.marketplace.servlet;

import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.exception.AuthException;
import edu.usc.marketplace.model.Administrator;
import edu.usc.marketplace.model.Guest;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.model.Visitor;
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

    /** Request attribute where AuthFilter leaves the visitor it checked, for the servlet to use. */
    public static final String REQUEST_VISITOR = "visitor";

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

    /**
     * Who is making this request, built from the session alone: a Guest if nobody is logged in, otherwise
     * a Member (or Administrator) with the id, role and verified flag saved at login. The name, email and
     * suspension are not kept in the session; use loadVisitor() when those are needed.
     */
    public static Visitor currentVisitor(HttpServletRequest req) {
        Integer id = currentUserId(req);
        if (id == null) return Guest.INSTANCE;
        HttpSession session = req.getSession(false);
        if ("ADMIN".equals(session.getAttribute(BaseServlet.SESSION_ROLE))) {
            return new Administrator(id, null, null, null);
        }
        return new Member(id, null, null, null, isVerified(req));
    }

    /**
     * Who is making this request, with the user read fresh from the database. A suspension, a newly
     * verified email or a changed role therefore counts on the very next request, without a new login.
     * If the account no longer exists, the session is ended and the visitor is a Guest.
     */
    public static Visitor loadVisitor(HttpServletRequest req, UserDAO users) {
        Integer id = currentUserId(req);
        if (id == null) return Guest.INSTANCE;
        User user = users.findById(id);
        if (user == null) {
            endSession(req);
            return Guest.INSTANCE;
        }
        HttpSession session = req.getSession(false);
        session.setAttribute(BaseServlet.SESSION_ROLE, user.getRole());
        session.setAttribute(SESSION_VERIFIED, user.isVerified());
        return Member.from(user);
    }

    /**
     * The visitor AuthFilter already checked for this request, so servlets do not read the user twice.
     * Falls back to the session for requests the filter let through without a lookup (browsing).
     */
    public static Visitor visitor(HttpServletRequest req) {
        Object checked = req.getAttribute(REQUEST_VISITOR);
        return checked instanceof Visitor ? (Visitor) checked : currentVisitor(req);
    }

    public static void endSession(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
    }
}
