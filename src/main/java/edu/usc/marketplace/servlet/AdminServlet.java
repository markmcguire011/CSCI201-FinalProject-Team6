package edu.usc.marketplace.servlet;

import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.dao.ReportDAO;
import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.exception.ForbiddenException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.Report;
import edu.usc.marketplace.model.ReportAction;
import edu.usc.marketplace.model.ReportReason;
import edu.usc.marketplace.model.TargetType;
import edu.usc.marketplace.model.User;
import edu.usc.marketplace.service.AdminService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The Admin Dashboard (section 3, Resolving a report). Administrators only.
 *
 *   GET  /api/admin/reports                 the open reports, oldest first
 *   POST /api/admin/reports/{id}/resolve    body {"action": "HIDE_LISTING", "SUSPEND_7_DAYS", "SUSPEND_PERMANENT" or "DISMISS"}
 *
 * The role is read from the database on every request, not from the session, so an account that
 * stops being an administrator loses access right away.
 */
@WebServlet("/api/admin/*")
public class AdminServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public static final String NO_PERMISSION = "You don't have permission to do that";

    private transient ReportDAO reports;
    private transient ListingDAO listings;
    private transient UserDAO users;
    private transient AdminService admin;

    /** Used by Tomcat. */
    public AdminServlet() {
    }

    /** Used by tests. */
    public AdminServlet(ReportDAO reports, ListingDAO listings, UserDAO users, AdminService admin) {
        this.reports = reports;
        this.listings = listings;
        this.users = users;
        this.admin = admin;
    }

    @Override
    public void init() {
        if (reports == null) reports = new ReportDAO();
        if (listings == null) listings = new ListingDAO();
        if (users == null) users = new UserDAO();
        if (admin == null) admin = new AdminService();
    }

    /** One row of the Admin Dashboard: the report plus the names an administrator needs to judge it. */
    static final class ReportRow {
        int id;
        String targetType;
        int targetId;
        String targetLabel;     // the listing's title or the user's name
        Integer reportedUserId; // the user, or the seller of the listing
        String reportedUserName;
        String reason;
        String reasonLabel;
        String comment;
        String reporterName;
        LocalDateTime createdAt;
    }

    /** What a button on the dashboard sends. */
    private static final class ResolveForm {
        ReportAction action;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdministrator(req);
        if (!"/reports".equals(req.getPathInfo())) throw new NotFoundException("Page not found.");

        List<ReportRow> rows = new ArrayList<>();
        for (Report report : reports.findOpen()) rows.add(toRow(report));
        sendJson(resp, rows);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int adminId = requireAdministrator(req);

        // /reports/{id}/resolve
        String[] parts = req.getPathInfo() == null ? new String[0] : req.getPathInfo().split("/");
        if (parts.length != 4 || !parts[1].equals("reports") || !parts[3].equals("resolve")) {
            throw new NotFoundException("Page not found.");
        }
        int reportId;
        try {
            reportId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new NotFoundException("That report could not be found.");
        }

        ReportAction action = readJson(req, ResolveForm.class).action;
        Report resolved = admin.resolveReport(adminId, reportId, action, LocalDateTime.now());
        sendJson(resp, resolved);
    }

    /** Returns the administrator's id, or stops the request with 401 or 403. */
    private int requireAdministrator(HttpServletRequest req) {
        int id = SessionUtil.requireUserId(req);
        User user = users.findById(id);
        if (user == null || !user.isAdmin()) throw new ForbiddenException(NO_PERMISSION);
        return id;
    }

    private ReportRow toRow(Report report) {
        ReportRow row = new ReportRow();
        row.id = report.getId();
        row.targetType = report.getTargetType();
        row.targetId = report.getTargetId();
        row.reason = report.getReason();
        row.reasonLabel = ReportReason.valueOf(report.getReason()).getLabel();
        row.comment = report.getComment();
        row.createdAt = report.getCreatedAt();

        User reporter = users.findById(report.getReporterId());
        row.reporterName = reporter == null ? "Unknown user" : reporter.getFullName();

        if (TargetType.LISTING.name().equals(report.getTargetType())) {
            Listing listing = listings.findById(report.getTargetId());
            if (listing == null) {
                row.targetLabel = "Listing #" + report.getTargetId();
            } else {
                row.targetLabel = listing.getTitle() + (listing.isDeletedByAdmin() ? " (already hidden)" : "");
                row.reportedUserId = listing.getSellerId();
                row.reportedUserName = listing.getSellerName();
            }
        } else {
            User target = users.findById(report.getTargetId());
            row.targetLabel = target == null ? "User #" + report.getTargetId() : target.getFullName();
            row.reportedUserId = report.getTargetId();
            row.reportedUserName = row.targetLabel;
        }
        return row;
    }
}
