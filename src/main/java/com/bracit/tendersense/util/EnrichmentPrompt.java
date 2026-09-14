package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.Tender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The prompt and output schema for reading a tender.
 *
 * <p>The system prompt is identical for every tender, so Ollama keeps it cached and only
 * reads the tender itself on each call. The schema goes in Ollama's {@code format}
 * field, which constrains decoding to it -- far more reliable on a 7B model than asking
 * for JSON in the prompt.
 */
public final class EnrichmentPrompt {

    /** Part of each stored result: bumping it re-processes every tender. */
    public static final String VERSION = "enrich-2";

    public static final String SYSTEM = """
            You read Bangladeshi government tender notices for a company's tender team and \
            extract facts into the JSON schema you are given.
            Rules:
            - Use only facts the notice states. Never guess. Use null, or an empty list, \
            when the notice does not say.
            - short_title: at most 12 words. Say what is being procured, and where if a \
            place is named. Keep the notice's own words. Drop package numbers, reference \
            codes and scope details.
            - summary: one or two plain sentences a non-specialist understands: what the \
            buyer wants supplied or done.
            - deliverables: up to 5 short items the supplier must deliver or do, as the \
            notice describes them. Leave the list empty if the notice only repeats the title.
            - location: the district or city where the work happens, only if the notice names it.
            - min_turnover_bdt: the minimum annual turnover a bidder needs, in Bangladeshi \
            Taka as a plain number, only if stated.
            - min_experience_years: the minimum years of experience a bidder needs, only if stated.
            - certifications: certificates or licences a bidder must hold, named as in the notice.""";

    private static final int MAX_DESCRIPTION = 6000;
    private static final int MAX_ELIGIBILITY = 3000;

    /** "As per ToR", "As per TDS": the requirements are in a document we do not have. */
    private static final Pattern POINTS_ELSEWHERE = Pattern.compile("^\\s*as\\s+per\\b", Pattern.CASE_INSENSITIVE);

    private EnrichmentPrompt() {
    }

    public static boolean needsShortTitle(Tender t, int longTitleChars) {
        return t.getTitle() != null && t.getTitle().strip().length() > longTitleChars;
    }

    /** True when there is eligibility text worth reading, not just a pointer to another document. */
    public static boolean hasEligibilityText(Tender t) {
        String e = t.getEligibilityText();
        return e != null && e.strip().length() >= 30 && !POINTS_ELSEWHERE.matcher(e).find();
    }

    public static String user(Tender t) {
        StringBuilder sb = new StringBuilder();
        sb.append("TITLE:\n").append(nullToEmpty(t.getTitle())).append("\n\n");
        sb.append("NOTICE:\n").append(cut(withoutCategoryList(t), MAX_DESCRIPTION)).append("\n");
        if (hasEligibilityText(t)) {
            sb.append("\nELIGIBILITY:\n").append(cut(t.getEligibilityText(), MAX_ELIGIBILITY)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Only the fields worth the model's time for this tender: no short title for a title
     * that is already short, no requirement fields when there is no eligibility text.
     * Each field left out saves output tokens, the slowest part on CPU.
     */
    public static Map<String, Object> schema(boolean shortTitle, boolean requirements) {
        Map<String, Object> props = new LinkedHashMap<>();
        // Length limits are enforced while the model writes: a field that starts to loop
        // ("Package 1111…") is cut off and the reply stays valid JSON.
        if (shortTitle) {
            props.put("short_title", text(90));
        }
        props.put("summary", text(400));
        props.put("deliverables", Map.of("type", "array", "items", text(120), "maxItems", 5));
        props.put("location", Map.of("anyOf", List.of(text(60), Map.of("type", "null"))));
        if (requirements) {
            props.put("min_turnover_bdt", Map.of("type", List.of("number", "null")));
            props.put("min_experience_years", Map.of("type", List.of("integer", "null")));
            props.put("certifications", Map.of("type", "array", "items", text(60), "maxItems", 5));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", new ArrayList<>(props.keySet()));
        return schema;
    }

    private static Map<String, Object> text(int maxLength) {
        return Map.of("type", "string", "maxLength", maxLength);
    }

    /**
     * e-GP's category list (CPV) is appended to the description so matching sees it. The
     * model reads a category list as things to deliver -- "Tractors" for a hospital's
     * equipment tender -- so it is left out of what the model reads.
     */
    static String withoutCategoryList(Tender t) {
        String d = nullToEmpty(t.getDescription());
        String cpv = t.getCpvRaw();
        return cpv == null || cpv.isBlank() ? d : d.replace(cpv, "").strip();
    }

    private static String cut(String s, int max) {
        String v = nullToEmpty(s).strip();
        return v.length() <= max ? v : v.substring(0, max) + " …";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
