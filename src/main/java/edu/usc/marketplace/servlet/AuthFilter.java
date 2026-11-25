package edu.usc.marketplace.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.model.Action;
import edu.usc.marketplace.model.Visitor;
import edu.usc.marketplace.service.AccessPolicy;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Runs before every servlet under /api (section 4, Checking every request).
 *
 *   1. Look up which action the request is, e.g. POST /api/saved is SAVE (AccessPolicy.actionFor).
 *   2. Browsing is always allowed.
 *   3. Not logged in: answer "Login required" and change nothing.
 *   4. Suspended: end the session and answer the suspension message.
 *   5. Access level too low: answer "Verify your USC email to continue" or "You don't have permission to do that".
 *   6. Otherwise run the servlet.
 *
 * A refused request never reaches the servlet, so it cannot change anything. Errors are sent the same
 * way BaseServlet sends them: {"error": "message"} with status 401 or 403.
 */
@WebFilter("/api/*")
public class AuthFilter implements Filter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();   // keep ' as ', like BaseServlet

    private UserDAO users;

    /** Used by Tomcat. */
    public AuthFilter() {
    }

    /** Used by tests. */
    public AuthFilter(UserDAO users) {
        this.users = users;
    }

    @Override
    public void init(FilterConfig config) {
        if (users == null) users = new UserDAO();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        Action action = AccessPolicy.actionFor(req.getMethod(), pathInApp(req));
        if (action == Action.BROWSE) {
            chain.doFilter(request, response);   // no database lookup needed for public pages
            return;
        }

        // Read from the database, not the session, so a suspension counts on the very next click.
        Visitor visitor = SessionUtil.loadVisitor(req, users);
        String problem = AccessPolicy.check(visitor, action, LocalDateTime.now());
        if (problem != null) {
            refuse(req, resp, problem);
            return;
        }
        req.setAttribute(SessionUtil.REQUEST_VISITOR, visitor);
        chain.doFilter(request, response);
    }

    /** The address after the application name, e.g. "/api/listings/42". */
    static String pathInApp(HttpServletRequest req) {
        String info = req.getPathInfo();
        return req.getServletPath() + (info == null ? "" : info);
    }

    /** Sends the error and, for a suspended account, logs the user out. Shared with AdminFilter. */
    static void refuse(HttpServletRequest req, HttpServletResponse resp, String message) throws IOException {
        boolean guest = AccessPolicy.LOGIN_REQUIRED.equals(message);
        if (message.startsWith("This account is suspended")) SessionUtil.endSession(req);

        resp.setStatus(guest ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(Map.of("error", message)));
    }
}
