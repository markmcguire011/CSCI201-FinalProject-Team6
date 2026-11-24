package section3;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.ReportAction;
import edu.usc.marketplace.model.ReportStatus;
import edu.usc.marketplace.service.AdminService;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** Testing Plan 3f: Resolving a report. */
class ResolvingReportTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);

    /** 3f-U1 */
    @Test
    void sevenDaySuspensionEndsSevenDaysLater() {
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0),
                AdminService.suspensionEnd(ReportAction.SUSPEND_7_DAYS, NOW));
    }

    /** 3f-U2 */
    @Test
    void permanentSuspensionEndsInYear9999() {
        assertEquals(LocalDateTime.of(9999, 12, 31, 0, 0),
                AdminService.suspensionEnd(ReportAction.SUSPEND_PERMANENT, NOW));
    }

    /** 3f-U3 */
    @Test
    void dismissIsNotASuspension() {
        assertThrows(IllegalArgumentException.class,
                () -> AdminService.suspensionEnd(ReportAction.DISMISS, NOW));
    }

    /** 3f-U4 */
    @Test
    void userIsSuspendedDuringTheSevenDaysAndNotAfter() {
        Member member = new Member(5, "Sam", "sam@usc.edu", AccountType.STUDENT, true);
        member.setSuspendedUntil(AdminService.suspensionEnd(ReportAction.SUSPEND_7_DAYS, NOW));
        assertTrue(member.isSuspended(NOW.plusDays(6)));
        assertFalse(member.isSuspended(NOW.plusDays(7)));
    }

    /** 3f-U5 */
    @Test
    void openReportCanBeResolved() {
        assertDoesNotThrow(() -> AdminService.checkCanResolve(ReportStatus.OPEN));
    }

    /** 3f-U6 */
    @Test
    void reportAlreadyResolvedByAnotherAdmin() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AdminService.checkCanResolve(ReportStatus.RESOLVED));
        assertEquals("Another administrator already handled this report", e.getMessage());
    }
}
