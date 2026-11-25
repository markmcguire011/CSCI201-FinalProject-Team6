package edu.usc.marketplace.servlet;

import edu.usc.marketplace.dao.MessageDAO;
import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.exception.AuthException;
import edu.usc.marketplace.model.Action;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.service.AccessPolicy;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/me: tells the page who is logged in and what they can do (section 4). nav.js calls it on every page.
 *
 * Answers {id, fullName, initials, role, verified, unreadMessages, accessLevel, can}, or 401 "Login required"
 * for a guest. "can" has one true or false per action, e.g. {"MESSAGE": false, "REPORT": true}; access.js
 * uses it to turn buttons into "Verify your email to continue". The server still checks every request.
 * The user is read from the database each time, so a new login, a verified email or a changed role
 * shows up after a reload.
 */
@WebServlet("/api/me")
public class MeServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    private transient UserDAO users;
    private transient MessageDAO messages;

    /** Used by Tomcat. */
    public MeServlet() {
    }

    /** Used by tests. */
    public MeServlet(UserDAO users, MessageDAO messages) {
        this.users = users;
        this.messages = messages;
    }

    @Override
    public void init() {
        if (users == null) users = new UserDAO();
        if (messages == null) messages = new MessageDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = users.findById(SessionUtil.requireUserId(req));
        if (user == null) {
            SessionUtil.endSession(req);
            throw new AuthException(SessionUtil.LOGIN_REQUIRED);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("fullName", user.getFullName());
        body.put("initials", user.getInitials());
        body.put("role", user.getRole());
        body.put("verified", user.isVerified());
        body.put("unreadMessages", messages.unreadCount(user.getId()));

        Member member = Member.from(user);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Boolean> can = new LinkedHashMap<>();
        for (Action action : Action.values()) {
            can.put(action.name(), AccessPolicy.check(member, action, now) == null);
        }
        body.put("accessLevel", member.getAccessLevel().name());
        body.put("can", can);
        sendJson(resp, body);
    }
}
