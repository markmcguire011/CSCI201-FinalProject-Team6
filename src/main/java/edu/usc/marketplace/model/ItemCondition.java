package edu.usc.marketplace.model;

import java.util.Locale;

/**
 * The condition of a listed item (section 7), stored in listings.item_condition as the enum name, e.g. "LIKE_NEW".
 * Named ItemCondition to match the column and to avoid confusion with java.util.concurrent.locks.Condition.
 */
public enum ItemCondition {
    NEW("New"),
    LIKE_NEW("Like new"),
    GOOD("Good"),
    FAIR("Fair"),
    NOT_APPLICABLE("Not applicable");

    private final String label;

    ItemCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accepts the name ("LIKE_NEW") or the label ("Like new") in any letter case. Null for anything else. */
    public static ItemCondition fromString(String value) {
        if (value == null) return null;
        String v = value.trim();
        for (ItemCondition c : values()) {
            if (c.name().equalsIgnoreCase(v) || c.label.toLowerCase(Locale.ROOT).equals(v.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }
}
