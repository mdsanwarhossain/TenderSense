package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.NoticeType;
import com.bracit.tendersense.entity.enums.OpenTo;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.TenderCategory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts every portal's tender into one standard form, by rules.
 *
 * <p>The portals describe the same things in their own words -- e-GP's "Goods" is World
 * Bank's "GO", e-GP's "Quality Cost Based Selection (QCBS)" is World Bank's "Quality And
 * Cost-Based Selection" -- and some columns mean different things per portal. This maps
 * the values actually seen in the database onto one vocabulary, so the screens can use
 * generic labels. Deterministic and instant: no model is needed for any of it.
 */
public final class TenderStandardiser {

    private static final Pattern AMENDMENTS = Pattern.compile("issued\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * World Bank notices for Bangladesh embed the e-GP notice as flattened text:
     * "... Procuring Entity District : Dhaka Procurement Nature : Goods ...". A value
     * runs until the next of these labels.
     */
    private static final List<String> EGP_LABELS = List.of(
            "Ministry", "Division", "Organization", "Procuring Entity Name", "Procuring Entity Code",
            "Procuring Entity District", "Procurement Nature", "Procurement Type", "Event Type",
            "Invitation for", "Invitation Ref No", "Invitation Reference No", "App ID",
            "Tender/Proposal ID", "Key Information", "Procurement Method", "Budget and Source of Funds",
            "Budget Type", "Source of Funds", "Development Partner", "Project Code", "Project Name",
            "Tender/Proposal Package No", "Package No", "Tender/Proposal Publication Date",
            "Tender Publication Date", "Scheduled Tender", "Tender Closing Date");

    /** Same method, different spellings -> one wording. Keys are lower-case, codes stripped. */
    private static final Map<String, String> METHODS = Map.ofEntries(
            Map.entry("open tendering", "Open tender"),
            Map.entry("request for bids", "Open tender"),
            Map.entry("one stage two envelopes tendering", "Open tender, two envelopes"),
            Map.entry("individual consultant", "Individual consultant"),
            Map.entry("individual consultant selection", "Individual consultant"),
            Map.entry("quality cost based selection", "Quality and cost based selection"),
            Map.entry("quality and cost-based selection", "Quality and cost based selection"),
            Map.entry("selection under a fixed budget", "Fixed budget selection"),
            Map.entry("fixed budget selection", "Fixed budget selection"),
            Map.entry("request for quotations", "Request for quotations"),
            Map.entry("direct selection", "Direct selection"),
            Map.entry("consultant qualification selection", "Consultant qualification selection"),
            Map.entry("least cost selection", "Least cost selection"),
            Map.entry("quality based selection", "Quality based selection"),
            Map.entry("limited tendering", "Limited tender"));

    private TenderStandardiser() {
    }

    public static void apply(Tender t) {
        SourcePortal portal = t.getSourcePortal();
        String notice = t.getDescription() == null ? "" : t.getDescription();

        switch (portal) {
            case EGP_BANGLADESH -> {
                t.setBuyer(joinDistinct(", ", t.getProcuringEntity(), t.getOrganization()));
                t.setPartOf(joinDistinct(" › ", t.getMinistry(), t.getDivision()));
                t.setLocation(blankToNull(t.getDistrict()));
                t.setFundedBy(blankToNull(t.getSourceOfFunds()));
            }
            case WORLD_BANK -> {
                t.setBuyer(firstNonBlank(labelled(notice, "Procuring Entity Name"),
                        labelled(notice, "Organization")));
                t.setPartOf(blankToNull(t.getOrganization()));   // the World Bank project name
                t.setLocation(labelled(notice, "Procuring Entity District"));
                t.setFundedBy("World Bank");
            }
            case UNGM -> {
                t.setBuyer(blankToNull(t.getOrganization()));   // the UN agency
                t.setFundedBy(blankToNull(t.getOrganization()));
            }
            case ISDB -> {
                t.setBuyer("Islamic Development Bank (IsDB)");
                t.setFundedBy("Islamic Development Bank (IsDB)");
            }
        }
        if (t.getLocation() == null) {
            t.setLocation(blankToNull(t.getCountry()));
        }

        t.setCategory(category(portal, t.getProcurementNature()));
        t.setNoticeType(noticeType(portal, portal == SourcePortal.EGP_BANGLADESH
                ? t.getNoticeTypeRaw() : t.getProcurementType()));
        t.setOpenTo(portal == SourcePortal.EGP_BANGLADESH ? openTo(t.getProcurementType()) : null);
        t.setMethodLabel(method(t.getProcurementMethod()));
        t.setAmendments(amendments(t.getStatus()));
    }

    static TenderCategory category(SourcePortal portal, String nature) {
        if (nature == null || nature.isBlank()) {
            return null;
        }
        String n = nature.strip().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "go" -> TenderCategory.GOODS;
            case "cw" -> TenderCategory.WORKS;
            case "cs" -> TenderCategory.CONSULTING;
            case "nc" -> TenderCategory.OTHER_SERVICES;
            default -> {
                if (n.startsWith("goods")) yield TenderCategory.GOODS;
                if (n.startsWith("works")) yield TenderCategory.WORKS;
                if (n.startsWith("physical")) yield TenderCategory.OTHER_SERVICES;
                // e-GP files consultancy (REOI, QCBS, individual consultant) as "Services".
                if (n.startsWith("services") || n.contains("consult")) yield TenderCategory.CONSULTING;
                yield null;
            }
        };
    }

    static NoticeType noticeType(SourcePortal portal, String raw) {
        if (raw == null || raw.isBlank()) {
            // e-GP pages before this parser version carried no event type; e-GP only
            // lists open notices, so a tender is the safe reading.
            return portal == SourcePortal.EGP_BANGLADESH ? NoticeType.TENDER : null;
        }
        String r = raw.toLowerCase(Locale.ROOT);
        if (r.contains("award")) return NoticeType.CONTRACT_AWARD;
        if (r.contains("general procurement")) return NoticeType.GENERAL_NOTICE;
        if (r.contains("prequal") || r.equals("pq")) return NoticeType.PREQUALIFICATION;
        if (r.contains("expression") || r.contains("eoi")) return NoticeType.EXPRESSION_OF_INTEREST;
        return NoticeType.TENDER;
    }

    static OpenTo openTo(String type) {
        if (type == null) return null;
        return switch (type.strip().toUpperCase(Locale.ROOT)) {
            case "NCT" -> OpenTo.NATIONAL;
            case "ICT" -> OpenTo.INTERNATIONAL;
            default -> null;
        };
    }

    /** "Quality Cost Based Selection (QCBS)" and "Quality And Cost-Based Selection" read the same. */
    static String method(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
        // "Open Tendering Method" is looked up without its trailing "method"; a name we do
        // not know keeps every word it came with.
        String known = METHODS.get(key.replaceAll("\\s*\\bmethod$", ""));
        if (known == null) {
            known = METHODS.get(key);
        }
        if (known != null) {
            return known;
        }
        return key.isEmpty() ? null : Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    static Integer amendments(String status) {
        if (status == null) {
            return null;
        }
        Matcher m = AMENDMENTS.matcher(status);
        return m.find() ? Integer.valueOf(m.group(1)) : 0;
    }

    /** The value after "Label :" in flattened e-GP text, up to the next known label. */
    static String labelled(String text, String label) {
        Matcher m = Pattern.compile(Pattern.quote(label) + "\\s*:\\s*", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!m.find()) {
            return null;
        }
        String rest = text.substring(m.end());
        int end = Math.min(rest.length(), 160);
        for (String next : EGP_LABELS) {
            // "(?:^|\\s)": an empty field is followed straight away by the next label.
            Matcher n = Pattern.compile("(?:^|\\s)" + Pattern.quote(next) + "\\b[^:]{0,40}:", Pattern.CASE_INSENSITIVE)
                    .matcher(rest);
            if (n.find() && n.start() < end) {
                end = n.start();
            }
        }
        String value = rest.substring(0, end).strip();
        // A colon left in the value means a label was taken for one: better nothing than that.
        return value.isEmpty() || value.length() > 150 || value.contains(":") ? null : value;
    }

    private static String joinDistinct(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            String v = blankToNull(p);
            if (v == null || sb.toString().toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(sep);
            }
            sb.append(v);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (blankToNull(v) != null) {
                return v.strip();
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
