package section3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.service.EmailVerificationService;
import edu.usc.marketplace.service.EmailVerificationService.Result;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testing Plan 3c: Verifying the email. Each test makes a new EmailVerificationService and passes in
 * the time, so expiry can be tested without waiting.
 */
class VerifyingEmailTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 8, 12, 0);

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService();
    }

    /** 3c-U1 */
    @Test
    void codeIsExactlySixDigits() {
        for (int i = 0; i < 100; i++) {
            assertTrue(service.sendCode(1, START).matches("\\d{6}"));
        }
    }

    /** 3c-U2 */
    @Test
    void rightCodeWithinFifteenMinutesWorks() {
        String code = service.sendCode(1, START);
        assertEquals(Result.OK, service.verify(1, code, START.plusMinutes(15)));
        assertNull(service.getCode(1));
    }

    /** 3c-U3 */
    @Test
    void wrongCodeIsRejected() {
        String code = service.sendCode(1, START);
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertEquals(Result.WRONG, service.verify(1, wrong, START.plusMinutes(1)));
    }

    /** 3c-U4 */
    @Test
    void codeAfterFifteenMinutesIsExpired() {
        String code = service.sendCode(1, START);
        assertEquals(Result.EXPIRED, service.verify(1, code, START.plusMinutes(15).plusSeconds(1)));
    }

    /** 3c-U5 */
    @Test
    void verifyingWithNoCodeSent() {
        assertEquals(Result.NO_CODE, service.verify(1, "123456", START));
    }

    /** 3c-U6 */
    @Test
    void codeCannotBeUsedTwice() {
        String code = service.sendCode(1, START);
        service.verify(1, code, START);
        assertEquals(Result.NO_CODE, service.verify(1, code, START));
    }

    /** 3c-U7 */
    @Test
    void resendLimitOfOncePerMinute() {
        service.sendCode(1, START);
        assertEquals(Result.TOO_SOON, service.resend(1, START.plusSeconds(59)));
        assertEquals(Result.SENT, service.resend(1, START.plusSeconds(60)));
    }

    /** 3c-U8 */
    @Test
    void afterResendOnlyTheNewCodeWorks() {
        String oldCode = service.sendCode(1, START);
        service.resend(1, START.plusMinutes(2));
        String newCode = service.getCode(1).getCode();
        if (!oldCode.equals(newCode)) {
            assertEquals(Result.WRONG, service.verify(1, oldCode, START.plusMinutes(3)));
        }
        assertEquals(Result.OK, service.verify(1, newCode, START.plusMinutes(3)));
    }

    /** 3c-U9 */
    @Test
    void codesForDifferentUsersDoNotMix() {
        String code1 = service.sendCode(1, START);
        service.sendCode(2, START);
        assertEquals(Result.OK, service.verify(1, code1, START));
        assertNotNull(service.getCode(2));
    }
}
