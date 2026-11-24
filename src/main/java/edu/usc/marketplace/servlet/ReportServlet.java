package edu.usc.marketplace.servlet;

import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.dao.ReportDAO;
import edu.usc.marketplace.dao.UserDAO;
import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.Report;
import edu.usc.marketplace.model.ReportForm;
import edu.usc.marketplace.model.TargetType;
import edu.usc.marketplace.service.Validator;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /api/reports: reports a listing or a user (section 3, Reporting).
 *
 * Body: {"targetType": "LISTING" or "USER", "targetId": 42, "reason": "SCAM", "comment": "optional"}
 *
 * Any logged-in user can report, verified or not. A user can report the same listing or user only once;
 * the database's unique rule also blocks a second request sent at the same moment.
 */
@WebServlet("/api/reports")
public class ReportServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public static final String ALREADY_REPORTED = "You already reported this";
    public static final String THANKS = "Thanks, an administrator will review your report";

    private transient ReportDAO reports;
    private transient ListingDAO listings;
    private transient UserDAO users;

    /** Used by Tomcat. */
    public ReportServlet() {
    }

    /** Used by tests. */
    public ReportServlet(ReportDAO reports, ListingDAO listings, UserDAO users) {
        this.reports = reports;
        this.listings = listings;
        this.users = users;
    }

    @Override
    public void init() {
        if (reports == null) reports = new ReportDAO();
        if (listings == null) listings = new ListingDAO();
        if (users == null) users = new UserDAO();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int reporterId = SessionUtil.requireUserId(req);
        ReportForm form = readJson(req, ReportForm.class);

        Map<String, String> errors = Validator.validateReport(form);
        if (!errors.isEmpty()) throw new ValidationException(errors.values().iterator().next(), errors);

        if (form.targetType == TargetType.LISTING) {
            Listing listing = listings.findById(form.targetId);
            if (listing == null || listing.isDeletedByAdmin()) throw new NotFoundException("That listing could not be found.");
        } else if (users.findById(form.targetId) == null) {
            throw new NotFoundException("That user could not be found.");
        }

        if (reports.exists(reporterId, form.targetType.name(), form.targetId)) throw new ConflictException(ALREADY_REPORTED);

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(form.targetType.name());
        report.setTargetId(form.targetId);
        report.setReason(form.reason.name());
        String comment = form.comment == null ? "" : form.comment.trim();
        report.setComment(comment.isEmpty() ? null : comment);

        int reportId;
        try {
            reportId = reports.insert(report);
        } catch (ConflictException e) {
            throw new ConflictException(ALREADY_REPORTED);   // two fast clicks on Submit
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", reportId);
        body.put("message", THANKS);
        sendJson(resp, HttpServletResponse.SC_CREATED, body);
    }
}
