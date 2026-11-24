package edu.usc.marketplace.service;

import edu.usc.marketplace.model.VerificationCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 6-digit email verification code (section 3).
 *
 * A code expires 15 minutes after it is sent, and a new one can be requested once per minute.
 * Every method takes the current time as a parameter, so tests can check expiry without waiting.
 *
 * Where codes are kept and how they are emailed are passed in: the app uses the database and real email
 * (see VerifyServlet), and the no-argument constructor keeps codes in memory and sends nothing, for tests.
 */
public class EmailVerificationService {
    public static final int CODE_MINUTES = 15;
    public static final int RESEND_SECONDS = 60;

    public enum Result { OK, WRONG, EXPIRED, NO_CODE, SENT, TOO_SOON }

    /** Where codes are saved: one code per user. */
    public interface CodeStore {
        VerificationCode find(int userId);

        /** Saves the code, replacing the user's old one. */
        void save(VerificationCode code);

        void delete(int userId);
    }

    /** Delivers a code to the user, e.g. by email. */
    public interface CodeSender {
        void send(int userId, String code);
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodeStore store;
    private final CodeSender sender;

    /** For tests: codes are kept in memory and nothing is emailed. */
    public EmailVerificationService() {
        this(new MemoryStore(), (userId, code) -> { });
    }

    public EmailVerificationService(CodeStore store, CodeSender sender) {
        this.store = store;
        this.sender = sender;
    }

    /** Makes a new random code, saves it with an expiry 15 minutes away, sends it, and returns it. */
    public String sendCode(int userId, LocalDateTime now) {
        // %06d keeps the leading zeros, so 42 becomes "000042".
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        // The database keeps whole seconds, so drop the fraction here. Otherwise MySQL could round the sent
        // time up into the future and the countdown would start at 61.
        LocalDateTime sentAt = now.truncatedTo(ChronoUnit.SECONDS);
        store.save(new VerificationCode(userId, code, sentAt.plusMinutes(CODE_MINUTES), sentAt));
        sender.send(userId, code);
        return code;
    }

    /**
     * Checks the code the user typed. Expiry is checked before the code itself, so an old code is always
     * reported as EXPIRED. A correct code is deleted, so it cannot be used twice.
     */
    public Result verify(int userId, String typed, LocalDateTime now) {
        VerificationCode saved = store.find(userId);
        if (saved == null) return Result.NO_CODE;
        if (saved.isExpired(now)) return Result.EXPIRED;
        if (typed == null || !sameText(saved.getCode(), typed.trim())) return Result.WRONG;
        store.delete(userId);
        return Result.OK;
    }

    /** "Send a new code": refused if the last code was sent less than 60 seconds ago. */
    public Result resend(int userId, LocalDateTime now) {
        if (secondsUntilResend(userId, now) > 0) return Result.TOO_SOON;
        sendCode(userId, now);
        return Result.SENT;
    }

    /** How long the user must still wait before asking for a new code. 0 means they can ask now. */
    public long secondsUntilResend(int userId, LocalDateTime now) {
        VerificationCode saved = store.find(userId);
        if (saved == null) return 0;
        long waited = Duration.between(saved.getLastSentAt(), now).getSeconds();
        return Math.max(0, Math.min(RESEND_SECONDS, RESEND_SECONDS - waited));
    }

    /** The user's current code, or null if there is none. */
    public VerificationCode getCode(int userId) {
        return store.find(userId);
    }

    /** Compares every character, so the time it takes does not reveal how many digits were right. */
    private static boolean sameText(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static final class MemoryStore implements CodeStore {
        private final Map<Integer, VerificationCode> codes = new ConcurrentHashMap<>();

        @Override
        public VerificationCode find(int userId) {
            return codes.get(userId);
        }

        @Override
        public void save(VerificationCode code) {
            codes.put(code.getUserId(), code);
        }

        @Override
        public void delete(int userId) {
            codes.remove(userId);
        }
    }
}
