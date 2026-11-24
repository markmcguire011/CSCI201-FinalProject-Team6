package edu.usc.marketplace.service;

import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.dao.NotificationDAO;
import edu.usc.marketplace.dao.ReportDAO;
import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.Report;
import edu.usc.marketplace.model.ReportAction;
import edu.usc.marketplace.model.ReportReason;
import edu.usc.marketplace.model.ReportStatus;
import edu.usc.marketplace.model.TargetType;

import java.time.LocalDateTime;

/**
 * What an administrator can do with a report (section 3): hide the listing, suspend the user for 7 days
 * or permanently, or dismiss the report.
 *
 * Resolving a report runs as one transaction. The report row is locked first, so if two administrators
 * click at the same moment only one action is saved, and if anything fails nothing is changed.
 */
public class AdminService {
    /** A permanent suspension is stored as a date nobody will reach. */
    public static final LocalDateTime PERMANENT = LocalDateTime.of(9999, 12, 31, 0, 0);
    public static final int SUSPEND_DAYS = 7;

    public static final String ALREADY_HANDLED = "Another administrator already handled this report";
    public static final String NOTIFY_LISTING_REMOVED = "LISTING_REMOVED";

    private final Database db;
    private final ReportDAO reports;
    private final ListingDAO listings;
    private final UserDAO users;
    private final NotificationDAO notifications;

    public AdminService() {
        this(Database.get());
    }

    public AdminService(Database db) {
        this.db = db;
        this.reports = new ReportDAO(db);
        this.listings = new ListingDAO(db);
        this.users = new UserDAO(db);
        this.notifications = new NotificationDAO(db);
    }

    /** When a suspension that starts now would end. Only the two Suspend actions have an end. */
    public static LocalDateTime suspensionEnd(ReportAction action, LocalDateTime now) {
        switch (action) {
            case SUSPEND_7_DAYS:
                return now.plusDays(SUSPEND_DAYS);
            case SUSPEND_PERMANENT:
                return PERMANENT;
            default:
                throw new IllegalArgumentException(action + " is not a suspension");
        }
    }

    /** Only an Open report can be resolved. */
    public static void checkCanResolve(ReportStatus status) {
        if (status != ReportStatus.OPEN) throw new IllegalStateException(ALREADY_HANDLED);
    }

    /**
     * Carries out the administrator's choice and marks the report Resolved, saving who handled it,
     * what they did and when. Returns the report as it is after the change.
     */
    public Report resolveReport(int adminId, int reportId, ReportAction action, LocalDateTime now) {
        if (action == null) throw new ValidationException("Choose Hide, Suspend or Dismiss.");

        db.inTransaction(c -> {
            // FOR UPDATE: a second administrator waits here until this transaction ends.
            Report report = reports.findForUpdate(c, reportId);
            if (report == null) throw new NotFoundException("That report could not be found.");
            try {
                checkCanResolve(ReportStatus.valueOf(report.getStatus()));
            } catch (IllegalStateException e) {
                throw new ConflictException(ALREADY_HANDLED);
            }

            boolean aboutListing = TargetType.LISTING.name().equals(report.getTargetType());
            Listing listing = aboutListing ? listings.findForUpdate(c, report.getTargetId()) : null;

            switch (action) {
                case HIDE_LISTING:
                    if (!aboutListing) throw new ValidationException("Only a reported listing can be hidden.");
                    if (listing == null) throw new NotFoundException("That listing could not be found.");
                    listings.deleteByAdmin(c, listing.getId());
                    String reason = ReportReason.valueOf(report.getReason()).getLabel();
                    notifications.insert(c, listing.getSellerId(), NOTIFY_LISTING_REMOVED,
                            "Your listing \"" + listing.getTitle() + "\" was removed by an administrator. Reason: " + reason + ".",
                            null);
                    break;
                case SUSPEND_7_DAYS:
                case SUSPEND_PERMANENT:
                    // A report about a listing suspends the person who posted it.
                    if (aboutListing && listing == null) throw new NotFoundException("That listing could not be found.");
                    int userId = aboutListing ? listing.getSellerId() : report.getTargetId();
                    users.suspendUntil(c, userId, suspensionEnd(action, now));
                    break;
                case DISMISS:
                    break;   // nothing changes except the report itself
            }

            if (!reports.resolve(c, reportId, adminId, action.name())) throw new ConflictException(ALREADY_HANDLED);
            return null;
        });
        return reports.findById(reportId);
    }
}
