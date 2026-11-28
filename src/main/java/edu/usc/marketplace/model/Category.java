package edu.usc.marketplace.model;

import java.util.Locale;

/**
 * The category of a listing (section 7). The listings.category column stores the enum name, e.g. "TEXTBOOKS";
 * the page shows the label, e.g. "Textbooks".
 */
public enum Category {
    TEXTBOOKS("Textbooks"),
    ELECTRONICS("Electronics"),
    FURNITURE("Furniture"),
    CLOTHING("Clothing"),
    TICKETS("Tickets"),
    SERVICES("Services"),
    FOOD("Food");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accepts the name ("TEXTBOOKS") or the label ("Textbooks") in any letter case. Null for anything else. */
    public static Category fromString(String value) {
        if (value == null) return null;
        String v = value.trim();
        for (Category c : values()) {
            if (c.name().equalsIgnoreCase(v) || c.label.toLowerCase(Locale.ROOT).equals(v.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }
}
