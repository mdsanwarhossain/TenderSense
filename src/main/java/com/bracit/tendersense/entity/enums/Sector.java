package com.bracit.tendersense.entity.enums;

/**
 * The tag every tender carries, and the unit a company subscribes to.
 *
 * <p>Collapsed from the portal's own CPV categories — 277 distinct top-level values were
 * observed across the corpus, far too many to expose or subscribe to. These are the
 * coarse buckets a bidding firm actually thinks in.
 *
 * <p>{@link #OTHER} is deliberately scored for <em>every</em> organisation. A tender that
 * could not be classified must not be silently withheld from everyone because of a
 * classification miss — the sector gate is for tenders we understood, not for ones we did not.
 */
public enum Sector {

    IT_SERVICES,
    IT_HARDWARE,
    TELECOM,
    CONSULTANCY,

    CONSTRUCTION,
    ENGINEERING,
    MAINTENANCE,
    ELECTRICAL,
    MACHINERY,

    MEDICAL,
    HEALTH_SERVICES,
    EDUCATION,

    FOOD,
    AGRICULTURE,
    CHEMICALS,
    TEXTILES,
    FURNITURE,
    PRINTING,

    VEHICLES,
    LOGISTICS,
    UTILITIES,

    /** Manpower supply, security guards, cleaning — the category BracIT explicitly excludes. */
    STAFFING,

    /** Unclassified. Scored for everyone, by design. */
    OTHER;

    /** {@code IT_SERVICES} reads as noise in a chip; "IT services" does not. */
    public String label() {
        String[] words = name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i == 0) {
                out.append(w.equals("it") ? "IT" : Character.toUpperCase(w.charAt(0)) + w.substring(1));
            } else {
                out.append(' ').append(w);
            }
        }
        return out.toString();
    }
}
