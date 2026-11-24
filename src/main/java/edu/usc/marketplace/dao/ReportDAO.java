package edu.usc.marketplace.dao;

import edu.usc.marketplace.exception.ConflictException;
import edu.usc.marketplace.model.Report;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes the reports table. A user can report the same listing or user only once. */
public class ReportDAO extends BaseDAO<Report> {

    public ReportDAO(Database db) {
        super(db);
    }

    public ReportDAO() {
        super();
    }

    @Override
    protected Report mapRow(ResultSet rs) throws SQLException {
        Report r = new Report();
        r.setId(rs.getInt("report_id"));
        r.setReporterId(rs.getInt("reporter_id"));
        r.setTargetType(rs.getString("target_type"));
        r.setTargetId(rs.getInt("target_id"));
        r.setReason(rs.getString("reason"));
        r.setComment(rs.getString("comment"));
        r.setStatus(rs.getString("status"));
        r.setHandledBy(intOrNull(rs, "handled_by"));
        r.setActionTaken(rs.getString("action_taken"));
        r.setCreatedAt(time(rs, "created_at"));
        r.setResolvedAt(time(rs, "resolved_at"));
        return r;
    }

    public int insert(Report r) {
        try {
            return insert("INSERT INTO reports (reporter_id, target_type, target_id, reason, comment) VALUES (?, ?, ?, ?, ?)",
                    r.getReporterId(), r.getTargetType(), r.getTargetId(), r.getReason(), r.getComment());
        } catch (DataException e) {
            if (e.isDuplicateKey()) throw new ConflictException("You have already reported this.");
            throw e;
        }
    }

    /** True if this user already reported this listing or user (section 3). */
    public boolean exists(int reporterId, String targetType, int targetId) {
        return queryLong("SELECT COUNT(*) FROM reports WHERE reporter_id = ? AND target_type = ? AND target_id = ?",
                reporterId, targetType, targetId) > 0;
    }

    /**
     * Resolving a report, step 1 (inside Database.inTransaction): locks the report row until the
     * transaction ends, so two administrators cannot both act on the same report.
     */
    public Report findForUpdate(Connection c, int reportId) throws SQLException {
        return queryOne(c, "SELECT * FROM reports WHERE report_id = ? FOR UPDATE", reportId);
    }

    public Report findById(int reportId) {
        return queryOne("SELECT * FROM reports WHERE report_id = ?", reportId);
    }

    /** The Admin Dashboard: open reports, oldest first. */
    public List<Report> findOpen() {
        return queryList("SELECT * FROM reports WHERE status = 'OPEN' ORDER BY created_at");
    }

    /**
     * Marks a report resolved. Use it inside Database.inTransaction together with the action
     * (removing the listing, suspending the user) so both happen or neither does.
     * Returns false if another administrator already resolved it.
     */
    public boolean resolve(Connection c, int reportId, int adminId, String actionTaken) throws SQLException {
        return update(c, "UPDATE reports SET status = 'RESOLVED', handled_by = ?, action_taken = ?, resolved_at = NOW() "
                + "WHERE report_id = ? AND status = 'OPEN'", adminId, actionTaken, reportId) == 1;
    }
}
