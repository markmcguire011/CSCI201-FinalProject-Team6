
package edu.usc.marketplace.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Handles POST /api/logout.
 * Invalidates the user's current session.
 */
@WebServlet("/api/logout")
public class LogoutServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest req,
                          HttpServletResponse resp) throws IOException {

        SessionUtil.endSession(req);

        sendJson(resp, Map.of(
                "message", "Logged out."
        ));
    }
}
