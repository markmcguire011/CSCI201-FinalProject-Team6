
package edu.usc.marketplace.servlet;

import edu.usc.marketplace.model.User;
import edu.usc.marketplace.service.LoginResult;
import edu.usc.marketplace.service.LoginService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles POST /api/login.
 * Validates credentials and creates a new session on success.
 */
@WebServlet("/api/login")
public class LoginServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private transient LoginService loginService;

    public LoginServlet() {
    }

    public LoginServlet(LoginService loginService) {
        this.loginService = loginService;
    }

    @Override
    public void init() {
        if (loginService == null) {
            loginService = new LoginService();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req,
                          HttpServletResponse resp) throws IOException {

        LoginForm form = readJson(req, LoginForm.class);

        LoginResult result = loginService.login(
                form.email, form.password);

        if (!result.isSuccess()) {
            sendJson(resp, result.getStatusCode(),
                    Map.of("error", result.getMessage()));
            return;
        }

        User user = result.getUser();

        SessionUtil.startSession(
                req,
                user.getId(),
                user.getRole(),
                user.isVerified()
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("fullName", user.getFullName());
        body.put("role", user.getRole());
        body.put("verified", user.isVerified());
        body.put("message", result.getMessage());

        sendJson(resp, body);
    }

    /**
     * Fields received from the login page as JSON.
     */
    private static class LoginForm {
        String email;
        String password;
    }
}
