package edu.usc.marketplace.servlet;

import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.RegisterForm;
import edu.usc.marketplace.model.Role;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.service.EmailVerificationService;
import edu.usc.marketplace.service.PasswordHasher;
import edu.usc.marketplace.service.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /api/register: creates an account (section 3, Registering).
 *
 * Body: {"fullName", "email", "password", "confirmPassword", "accountType": "STUDENT" or "ALUMNI"}
 *
 * It stops at the first failure: an invalid form never reaches the database, and an email that is
 * already used never reaches the insert. On success the account is saved unverified, a 6-digit code
 * is emailed, and the user is logged in as unverified so the Verify page can open.
 */
@WebServlet("/api/register")
public class RegisterServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public static final String FIX_FIELDS = "Fix the fields marked in red and try again.";
    public static final String EMAIL_TAKEN = "An account with this email already exists.";

    private transient UserDAO users;
    private transient EmailVerificationService verification;

    /** Used by Tomcat. */
    public RegisterServlet() {
    }

    /** Used by tests. */
    public RegisterServlet(UserDAO users, EmailVerificationService verification) {
        this.users = users;
        this.verification = verification;
    }

    @Override
    public void init() {
        if (users == null) users = new UserDAO();
        if (verification == null) verification = VerifyServlet.defaultService(users);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        RegisterForm form = readJson(req, RegisterForm.class);

        Map<String, String> errors = Validator.validateRegistration(form);
        if (!errors.isEmpty()) throw new ValidationException(FIX_FIELDS, errors);

        String email = Validator.normalizeEmail(form.email);
        if (users.emailExists(email)) throw emailTaken();

        User user = new User();
        user.setFullName(form.fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(PasswordHasher.hash(form.password));
        user.setAccountType(form.accountType.name());

        int userId;
        try {
            userId = users.insert(user);
        } catch (ConflictException e) {
            // Two people registered the same email at the same moment: the database's unique rule stopped the second.
            throw emailTaken();
        }

        verification.sendCode(userId, LocalDateTime.now());
        SessionUtil.startSession(req, userId, Role.USER.name(), false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", userId);
        body.put("fullName", user.getFullName());
        body.put("email", email);
        body.put("verified", false);
        sendJson(resp, HttpServletResponse.SC_CREATED, body);
    }

    private static ValidationException emailTaken() {
        return new ValidationException(EMAIL_TAKEN, Map.of("email", EMAIL_TAKEN));
    }
}
