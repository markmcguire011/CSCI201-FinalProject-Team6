package edu.usc.marketplace.model;

/** The four reasons in the Report dialog. Stored in reports.reason. */
public enum ReportReason {
    SCAM("Scam"),
    PROHIBITED_ITEM("Prohibited item"),
    OFFENSIVE("Offensive"),
    OTHER("Other");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    /** The text shown to users, e.g. "Prohibited item". */
    public String getLabel() {
        return label;
    }
}
