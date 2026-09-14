package com.bracit.tendersense.util;

import com.bracit.tendersense.dto.TenderEnrichment;
import com.bracit.tendersense.entity.Tender;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the model's reading against the tender before anything is shown.
 *
 * <p>A 7B model on its own added "road construction" to a palisading tender. So every
 * field is checked against the source text, and a field that fails is dropped on its own
 * -- the rest of the answer is kept, and the screen falls back to the portal's data.
 */
public final class EnrichmentValidator {

    public record Result(TenderEnrichment kept, List<String> dropped) {
    }

    static final int MAX_SHORT_TITLE = 90;
    static final int MAX_SUMMARY = 400;
    static final int MAX_DELIVERABLE = 120;
    static final int MAX_LOCATION = 80;

    /** Short joining words a rewrite may use without them appearing in the original. */
    private static final Set<String> JOINERS = Set.of(
            "with", "from", "into", "under", "over", "near", "upon", "their", "this", "that",
            "through", "within", "including");

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private EnrichmentValidator() {
    }

    public static Result validate(TenderEnrichment in, Tender t, int longTitleChars) {
        List<String> dropped = new ArrayList<>();
        String source = String.join(" ", nz(t.getTitle()), nz(t.getDescription()), nz(t.getEligibilityText()));
        Set<String> vocabulary = words(source);
        String eligibility = String.join(" ", nz(t.getEligibilityText()), nz(t.getDescription()));

        String shortTitle = null;
        if (in.shortTitle() != null && EnrichmentPrompt.needsShortTitle(t, longTitleChars)) {
            shortTitle = shortTitle(in.shortTitle(), t, vocabulary, dropped);
        }

        String summary = clean(in.summary());
        if (summary != null && summary.length() > MAX_SUMMARY) {
            summary = cutAtSentence(summary, MAX_SUMMARY);
        }
        if (summary != null && summary.length() < 20) {
            dropped.add("summary: too short to be useful");
            summary = null;
        }

        // A deliverable found only in e-GP's category list is a category, not something asked for.
        String categories = flat(nz(t.getCpvRaw()));
        String body = flat(nz(t.getTitle()) + " " + EnrichmentPrompt.withoutCategoryList(t));
        List<String> deliverables = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String d : in.deliverables()) {
            String c = clean(d == null ? null : d.replaceFirst("^[-•*\\s]+", ""));
            if (c == null || c.length() > MAX_DELIVERABLE || !seen.add(c.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!categories.isEmpty() && categories.contains(flat(c)) && !body.contains(flat(c))) {
                dropped.add("deliverable '" + c + "' is only in the category list");
                continue;
            }
            deliverables.add(c);
            if (deliverables.size() == 5) {
                break;
            }
        }

        String location = location(in.location(), source, dropped);

        BigDecimal turnover = in.minTurnoverBdt();
        if (turnover != null && !statedAmount(turnover, eligibility)) {
            dropped.add("min_turnover_bdt: " + turnover.toPlainString() + " is not a figure in the notice");
            turnover = null;
        }

        Integer years = in.minExperienceYears();
        if (years != null && (years < 1 || years > 50 || !statedYears(years, eligibility))) {
            dropped.add("min_experience_years: " + years + " years is not stated in the notice");
            years = null;
        }

        List<String> certifications = new ArrayList<>();
        String flatEligibility = flat(nz(t.getEligibilityText()));
        for (String c : in.certifications()) {
            String v = clean(c);
            if (v == null || v.length() > 60) {
                continue;
            }
            if (flatEligibility.contains(flat(v))) {
                certifications.add(v);
            } else {
                dropped.add("certification '" + v + "' is not named in the eligibility text");
            }
        }

        return new Result(new TenderEnrichment(shortTitle, summary, deliverables, location,
                turnover, years, certifications.stream().limit(5).toList()), dropped);
    }

    private static String shortTitle(String raw, Tender t, Set<String> vocabulary, List<String> dropped) {
        String v = clean(raw);
        if (v == null) {
            return null;
        }
        v = v.replaceAll("^[\"'“”]+|[\"'“”]+$", "").replaceAll("\\.$", "").strip();
        if (v.length() < 10 || v.length() > MAX_SHORT_TITLE) {
            dropped.add("short_title: " + v.length() + " characters");
            return null;
        }
        if (v.equalsIgnoreCase(nz(t.getTitle()).strip())) {
            return null;
        }
        for (String w : NON_WORD.split(v.toLowerCase(Locale.ROOT))) {
            if (w.length() >= 4 && !JOINERS.contains(w) && !known(w, vocabulary)) {
                dropped.add("short_title: '" + w + "' is not in the tender");
                return null;
            }
        }
        return v;
    }

    /** Keeps only the parts of the location the notice actually names. */
    private static String location(String raw, String source, List<String> dropped) {
        String v = clean(raw);
        if (v == null) {
            return null;
        }
        String flatSource = flat(source);
        Set<String> parts = new LinkedHashSet<>();
        for (String p : v.split(",")) {
            String part = p.strip();
            // Place names have no digits; "26-27/11 Package 1111" is a reference code.
            if (!part.isEmpty() && !part.matches(".*\\d.*") && flatSource.contains(flat(part))) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            dropped.add("location: '" + v + "' is not named in the tender");
            return null;
        }
        String joined = String.join(", ", parts);
        return joined.length() <= MAX_LOCATION ? joined : null;
    }

    /** The figure must be one the notice states (lakh and crore included), give or take rounding. */
    private static boolean statedAmount(BigDecimal value, String text) {
        if (value.signum() <= 0) {
            return false;
        }
        for (BigDecimal a : MoneyTextParser.amounts(text)) {
            if (a.signum() > 0 && value.subtract(a).abs()
                    .divide(a, 6, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.005")) <= 0) {
                return true;
            }
        }
        return false;
    }

    /** "5 years", "05 (five) years", "minimum 5 yrs". */
    private static boolean statedYears(int years, String text) {
        return Pattern.compile("\\b0*" + years + "\\b[^.;\\n]{0,25}?\\b(?:years?|yrs)\\b",
                Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    private static boolean known(String w, Set<String> vocabulary) {
        return vocabulary.contains(w)
                || (w.endsWith("es") && vocabulary.contains(w.substring(0, w.length() - 2)))
                || (w.endsWith("s") && vocabulary.contains(w.substring(0, w.length() - 1)))
                || vocabulary.contains(w + "s")
                || vocabulary.contains(w + "es");
    }

    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        for (String w : NON_WORD.split(text.toLowerCase(Locale.ROOT))) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    private static String flat(String s) {
        return NON_WORD.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String cutAtSentence(String s, int max) {
        String head = s.substring(0, max);
        int end = head.lastIndexOf(". ");
        return end > 40 ? head.substring(0, end + 1) : head.substring(0, head.lastIndexOf(' ')) + "…";
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String v = s.replaceAll("\\s+", " ").strip();
        return v.isEmpty() || v.equalsIgnoreCase("null") ? null : v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
