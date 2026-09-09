package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses an e-GP {@code ViewTender.jsp} page into a {@link Tender}.
 *
 * <p>The page is a flat sequence of table cells where a cell ending in ':' is a
 * label and the following cell is its value; a row commonly holds two such pairs.
 * Parsing therefore keys off <em>label text</em>, never cell position -- the layout
 * shifts between procurement types, and roughly one page in eight uses a reduced
 * label set (RFQ and proposal variants omit the invitation block entirely).
 */
@Component
public class EgpHtmlParser {

    private static final DateTimeFormatter EGP_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);

    /** Label cells longer than this are prose, not labels. */
    private static final int MAX_LABEL_LENGTH = 130;

    /** Bump whenever field extraction changes, to force a re-parse on ingest. */
    public static final String PARSER_VERSION = "egp-3";

    public Map<String, String> extractLabelValues(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style").remove();

        Elements cells = doc.select("td, th");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String text = normalise(cells.get(i).text());
            if (!text.endsWith(":") || text.length() > MAX_LABEL_LENGTH || text.length() < 4) {
                continue;
            }
            String label = text.substring(0, text.length() - 1).trim();
            String value = "";
            if (i + 1 < cells.size()) {
                String next = normalise(cells.get(i + 1).text());
                // An immediately following label means this field was left blank.
                if (!next.endsWith(":")) {
                    value = next;
                }
            }
            out.putIfAbsent(label, value);
        }
        return out;
    }

    public Tender parse(String html, String externalId, String snapshotPath) {
        Map<String, String> f = extractLabelValues(html);
        Instant now = Instant.now();

        String title = firstNonBlank(
                f.get("Tender/Proposal Package No. and Description"),
                f.get("Brief Description of Goods and Related Service"),
                f.get("Brief Description of assignment"),
                f.get("Project Name"));

        return Tender.builder()
                .sourcePortal(SourcePortal.EGP_BANGLADESH)
                .externalId(externalId)
                .referenceNo(truncate(firstNonBlank(f.get("Invitation Reference No."),
                        f.get("Tender/Proposal Package No. and Description")), 512))
                .title(truncate(title, 2000))
                .description(buildDescription(f, title))
                .procurementNature(truncate(f.get("Procurement Nature"), 128))
                .procurementType(truncate(f.get("Procurement Type"), 64))
                .procurementMethod(truncate(f.get("Procurement Method"), 256))
                .ministry(truncate(f.get("Ministry"), 512))
                .division(truncate(f.get("Division"), 512))
                .organization(truncate(f.get("Organization"), 512))
                .procuringEntity(truncate(f.get("Procuring Entity Name"), 512))
                .peCode(truncate(f.get("Procuring Entity Code"), 64))
                .district(truncate(f.get("Procuring Entity District"), 128))
                .country("Bangladesh")
                .budgetType(truncate(f.get("Budget Type"), 128))
                .sourceOfFunds(truncate(f.get("Source of Funds"), 256))
                .documentPriceBdt(parseMoney(firstNonBlank(
                        f.get("Tender/Proposal Document Price (In BDT)"),
                        f.get("PPS Document Price (In BDT)"))))
                .publishedAt(parseDateTime(firstNonBlank(
                        f.get("Scheduled Tender/Proposal Publication Date and Time"),
                        f.get("PPS Publication Date and Time"))))
                .closingAt(parseDateTime(firstNonBlank(
                        f.get("Tender/Proposal Closing Date and Time"),
                        f.get("Closing Date and Time"))))
                .cpvRaw(f.get("Category"))
                .status(truncate(f.get("Tender/Proposal Status"), 64))
                .eligibilityText(buildEligibility(f))
                .rawSnapshotPath(snapshotPath)
                .contentHash(HashUtil.sha256(html))
                .parserVersion(PARSER_VERSION)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
    }

    /**
     * e-GP serves two page shapes. Goods and Works tenders use "Tenderer" labels;
     * consultancy/proposal (PPS) notices use "Consultant" labels and carry an
     * "Experience, Resources and delivery capacity required" block that states hard
     * requirements. Those consultancy notices are precisely the ones BracIT bids on,
     * so both shapes are first-class here rather than one being a fallback.
     */
    private static String buildEligibility(Map<String, String> f) {
        String base = firstNonBlank(
                f.get("Eligibility of Tenderer"),
                f.get("Eligibility of Consultant"));
        String experience = f.get("Experience, Resources and delivery capacity required");

        if (isBlank(experience)) {
            return base;
        }
        if (isBlank(base)) {
            return experience;
        }
        // Both fields often carry the same boilerplate ("As per tender documents").
        if (base.trim().equalsIgnoreCase(experience.trim())) {
            return base;
        }
        return base + "\n\n" + experience;
    }

    /** Matching reads this, so the consultancy blocks are folded in rather than dropped. */
    private static String buildDescription(Map<String, String> f, String title) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, f.get("Brief Description of Goods and Related Service"));
        appendIfPresent(sb, f.get("Brief Description of assignment"));
        appendIfPresent(sb, f.get("Experience, Resources and delivery capacity required"));
        appendIfPresent(sb, f.get("Category"));
        if (sb.isEmpty()) {
            return title;
        }
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (isBlank(value)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append(value);
    }

    static LocalDateTime parseDateTime(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), EGP_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    static BigDecimal parseMoney(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9.]", "");
        if (digits.isEmpty() || digits.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalise(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
