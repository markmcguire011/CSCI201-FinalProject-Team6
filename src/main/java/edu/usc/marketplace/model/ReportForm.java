package edu.usc.marketplace.model;

/** What the Report dialog sends. Checked by Validator.validateReport(). */
public class ReportForm {
    public TargetType targetType;
    public int targetId;
    public ReportReason reason;
    public String comment;

    public ReportForm() {
    }

    public ReportForm(TargetType targetType, int targetId, ReportReason reason, String comment) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.comment = comment;
    }
}
