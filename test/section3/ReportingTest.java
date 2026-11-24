package section3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.model.ReportForm;
import edu.usc.marketplace.model.ReportReason;
import edu.usc.marketplace.model.TargetType;
import edu.usc.marketplace.service.Validator;

import org.junit.jupiter.api.Test;

/** Testing Plan 3e: Reporting a listing or user. */
class ReportingTest {

    /** 3e-U1 */
    @Test
    void reportWithReasonAndNoCommentIsValid() {
        ReportForm form = new ReportForm(TargetType.LISTING, 42, ReportReason.SCAM, null);
        assertTrue(Validator.validateReport(form).isEmpty());
    }

    /** 3e-U2 */
    @Test
    void threeHundredCharacterCommentIsAllowed() {
        ReportForm form = new ReportForm(TargetType.USER, 17, ReportReason.OFFENSIVE, "a".repeat(300));
        assertTrue(Validator.validateReport(form).isEmpty());
    }

    /** 3e-U3 */
    @Test
    void threeHundredOneCharacterCommentIsRejected() {
        ReportForm form = new ReportForm(TargetType.USER, 17, ReportReason.OTHER, "a".repeat(301));
        assertEquals("Comment must be 300 characters or fewer", Validator.validateReport(form).get("comment"));
    }

    /** 3e-U4 */
    @Test
    void reportWithNoReasonIsRejected() {
        ReportForm form = new ReportForm(TargetType.LISTING, 42, null, "bad");
        assertEquals("Pick a reason", Validator.validateReport(form).get("reason"));
    }

    /** 3e-U5 */
    @Test
    void thereAreExactlyFourReasons() {
        assertEquals(4, ReportReason.values().length);
    }
}
