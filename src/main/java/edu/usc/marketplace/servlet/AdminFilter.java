package edu.usc.marketplace.servlet;

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

/**
 * Rejects anyone who is not an administrator (sections 3 and 4).
 *
 * Covers the Admin Dashboard's requests (/api/admin/...) and the proof-of-payment files (/admin/proofs/...),
 * which are outside /api and so are not seen by AuthFilter. The role is read from the database on every
 * request, so an account that stops being an administrator loses access right away.
 */
@WebFilter({"/api/admin/*", "/admin/*"})
public class AdminFilter implements Filter {
    private UserDAO users;

    /** Used by Tomcat. */
    public AdminFilter() {
    }

    /** Used by tests. */
    public AdminFilter(UserDAO users) {
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

        Visitor visitor = SessionUtil.loadVisitor(req, users);
        String problem = AccessPolicy.check(visitor, Action.ADMIN, LocalDateTime.now());
        if (problem != null) {
            AuthFilter.refuse(req, (HttpServletResponse) response, problem);
            return;
        }
        req.setAttribute(SessionUtil.REQUEST_VISITOR, visitor);
        chain.doFilter(request, response);
    }
}
