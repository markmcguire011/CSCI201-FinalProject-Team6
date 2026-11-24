package edu.usc.marketplace.servlet;

import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.dao.VerificationDAO;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.model.VerificationCode;
import edu.usc.marketplace.service.EmailVerificationService;
import edu.usc.marketplace.service.EmailVerificationService.CodeStore;
import edu.usc.marketplace.service.EmailVerificationService.Result;
import edu.usc.marketplace.service.Mailer;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Verify page (section 3, Verifying the email). The user must be logged in.
 *
 *   GET  /api/verify          who is verifying, and how many seconds until a new code can be sent
 *   POST /api/verify          body {"code": "123456"}: checks the code and marks the account verified
 *   POST /api/verify/resend   "Send a new code", at most once per minute
 */
@WebServlet({"/api/verify", "/api/verify/resend"})
public class VerifyServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public static final String WRONG_CODE = "That code is not right. Check the email and try again.";
    public static final String EXPIRED_CODE = "This code expired. Send a new one.";
    public static final String NO_CODE = "Send a new code to continue.";

    private transient UserDAO users;
    private transient EmailVerificationService verification;

    /** Used by Tomcat. */
    public VerifyServlet() {
    }

    /** Used by tests. */
    public VerifyServlet(UserDAO users, EmailVerificationService verification) {
        this.users = users;
        this.verification = verification;
    }

    @Override
    public void init() {
        if (users == null) users = new UserDAO();
        if (verification == null) verification = defaultService(users);
    }

    /** What the Verify page sends. */
    private static final class CodeForm {
        String code;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = currentUser(req);
        sendJson(resp, status(user, LocalDateTime.now()));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = currentUser(req);
        LocalDateTime now = LocalDateTime.now();

        if (req.getServletPath().endsWith("/resend")) {
            resend(user, now);
        } else if (!user.isVerified()) {
            verify(req, user, now);
            user.setVerified(true);
        }
        sendJson(resp, status(user, now));
    }

    private void verify(HttpServletRequest req, User user, LocalDateTime now) throws IOException {
        String code = readJson(req, CodeForm.class).code;
        Result result = verification.verify(user.getId(), code, now);
        switch (result) {
            case OK:
                users.setVerified(user.getId());
                SessionUtil.markVerified(req);
                return;
            case EXPIRED:
                throw new ValidationException(EXPIRED_CODE);
            case NO_CODE:
                throw new ValidationException(NO_CODE);
            default:
                throw new ValidationException(WRONG_CODE);
        }
    }

    private void resend(User user, LocalDateTime now) {
        if (user.isVerified()) throw new ValidationException("Your email is already verified.");
        if (verification.resend(user.getId(), now) == Result.TOO_SOON) {
            long seconds = verification.secondsUntilResend(user.getId(), now);
            throw new ValidationException("Wait " + seconds + " seconds to request a new code");
        }
    }

    private Map<String, Object> status(User user, LocalDateTime now) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", user.getEmail());
        body.put("verified", user.isVerified());
        body.put("secondsUntilResend", user.isVerified() ? 0 : verification.secondsUntilResend(user.getId(), now));
        return body;
    }

    private User currentUser(HttpServletRequest req) {
        User user = users.findById(SessionUtil.requireUserId(req));
        if (user == null) {
            SessionUtil.endSession(req);
            throw new NotFoundException("This account could not be found. Register again.");
        }
        return user;
    }

    /** The service the app uses: codes are saved in verification_codes and sent by email. */
    static EmailVerificationService defaultService(UserDAO users) {
        VerificationDAO dao = new VerificationDAO();
        CodeStore store = new CodeStore() {
            @Override
            public VerificationCode find(int userId) {
                VerificationDAO.Code row = dao.find(userId);
                return row == null ? null : new VerificationCode(row.userId(), row.code(), row.expiresAt(), row.lastSentAt());
            }

            @Override
            public void save(VerificationCode code) {
                dao.save(code.getUserId(), code.getCode(), code.getExpiresAt(), code.getLastSentAt());
            }

            @Override
            public void delete(int userId) {
                dao.delete(userId);
            }
        };
        return new EmailVerificationService(store, (userId, code) -> {
            User user = users.findById(userId);
            if (user == null) return;
            Mailer.get().send(user.getEmail(), "Your USC Marketplace code: " + code,
                    "Hi " + user.getFullName() + ",\n\n"
                    + "Your USC Marketplace verification code is " + code + ".\n"
                    + "It expires in " + EmailVerificationService.CODE_MINUTES + " minutes.\n\n"
                    + "If you did not create an account, you can ignore this email.");
        });
    }
}
