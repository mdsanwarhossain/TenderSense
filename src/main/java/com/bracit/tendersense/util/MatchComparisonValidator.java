package com.bracit.tendersense.util;

import com.bracit.tendersense.dto.MatchComparison;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Tender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the written comparison line by line before a bid team reads it.
 *
 * <p>The small model that writes these follows the shape of the answer but not always its
 * rules. On the first real tender it returned three matches, two of which named the same
 * phrase on both sides of the dash, and three "gaps" that were each a second comparison
 * sentence crediting the company with the very work said to be missing. Both are
 * mechanical mistakes, so they are caught mechanically here rather than argued with in
 * the prompt.
 *
 * <p>A bad line is dropped on its own and the rest is kept, matching how the tender
 * enrichment is validated: a thinner answer is fine, a wrong one is not.
 */
public final class MatchComparisonValidator {

    /**
     * @param kept    what is safe to show
     * @param dropped why each rejected line went, for the log
     */
    public record Result(MatchComparison kept, List<String> dropped) {
    }

    static final int MAX_MATCHES = 4;
    static final int MAX_GAPS = 3;
    static final int MAX_COMPARISON = 420;
    static final int MAX_SENTENCES = 2;

    /** Either dash: the model is asked for an em dash but reaches for a hyphen too. */
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[—–-]\\s+");

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * A gap made only of these words says nothing, and they are also what makes an
     * overlap with the profile look larger than it is.
     */
    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "and", "or", "of", "for", "to", "in", "on", "with", "by", "from", "at",
            "as", "its", "it", "this", "that", "these", "those", "their", "is", "are", "be", "will",
            "shall", "must", "should", "not", "no", "any", "all", "such", "which", "who", "whom",
            "we", "our", "they", "has", "have", "had", "than", "then", "but", "also", "company",
            "tender", "services", "service", "work", "works", "provide", "providing", "support");

    /** Below this, a line's first half is not talking about the tender at all. */
    private static final double COVERED_BY_PROFILE = 0.8;

    /**
     * How much of a gap has to appear in a match's tender-side before the two are the
     * same thing said twice. The model did exactly this on the first real tender: it
     * matched "National Consultant - Digital Transformation Specialist" and then listed
     * "Hiring a national consultant" as missing. Those two share two words of three -- the
     * odd one out being a verb the tender itself uses -- so the bar sits below that.
     */
    private static final double CONTRADICTS_A_MATCH = 0.6;

    private MatchComparisonValidator() {
    }

    public static Result validate(MatchComparison in, Tender tender, CapabilityProfile profile) {
        List<String> dropped = new ArrayList<>();
        Set<String> tenderWords = words(tenderText(tender));
        Set<String> profileWords = words(profileText(profile));

        List<String> matches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> asks = new LinkedHashSet<>();
        for (String raw : nz(in.matches())) {
            String line = clean(raw);
            if (line == null || !seen.add(normalise(line))) {
                continue;
            }
            String[] halves = halves(line);
            if (halves == null || halves[0].isBlank() || halves[1].isBlank()) {
                dropped.add("match that names only one side: " + line);
            } else if (normalise(halves[0]).equals(normalise(halves[1]))) {
                dropped.add("match that names the same thing twice: " + line);
            } else if (!mentions(halves[0], tenderWords)) {
                // The left side is supposed to be the tender's ask. When it is more of the
                // company's own words, the line says nothing about this tender.
                dropped.add("match whose first side is not from the tender: " + line);
            } else if (!asks.add(normalise(halves[0]))) {
                // One ask, one line. The model answered a single requirement three times
                // over, once per service it thought covered it, which reads as three
                // separate findings.
                dropped.add("match repeating an ask already listed: " + line);
            } else if (matches.size() < MAX_MATCHES) {
                matches.add(line);
            }
        }

        List<String> gaps = new ArrayList<>();
        seen.clear();
        for (String raw : nz(in.gaps())) {
            String line = clean(raw);
            if (line == null || !seen.add(normalise(line))) {
                continue;
            }
            // Written as a comparison ("X - the profile does not mention X"), which is not
            // the shape asked for, but the left half is a real missing requirement. Keep
            // that rather than losing it to a formatting mistake.
            String[] sides = halves(line);
            if (sides != null) {
                if (sides[0].isBlank()) {
                    dropped.add("gap with nothing on its own side: " + line);
                    continue;
                }
                dropped.add("gap rewritten to its missing half: " + line);
                line = sides[0].strip();
            }
            if (namesTheCompany(line, profile)) {
                dropped.add("gap that credits the company: " + line);
            } else if (covered(line, profileWords)) {
                dropped.add("gap the profile already covers: " + line);
            } else if (alreadyMatched(line, matches)) {
                // Nothing is both covered and missing.
                dropped.add("gap the model also listed as a match: " + line);
            } else if (gaps.size() < MAX_GAPS) {
                gaps.add(line);
            }
        }

        return new Result(new MatchComparison(comparison(in.comparison()), matches, gaps), dropped);
    }

    /**
     * Two sentences, as asked for. The model runs past that when a profile is long: one
     * answer reached the token ceiling mid-word, which is not something to put on a page.
     */
    private static String comparison(String value) {
        String text = clean(value);
        if (text == null) {
            return null;
        }
        String[] sentences = text.split("(?<=[.!?])\\s+");
        int keep = Math.min(sentences.length, MAX_SENTENCES);
        // The model stops at its token ceiling, which can land mid-word. A sentence with
        // no end to it is not finished, so it is not shown.
        if (keep > 1 && !sentences[keep - 1].strip().matches(".*[.!?]$")) {
            keep--;
        }
        if (keep < sentences.length) {
            text = String.join(" ", Arrays.copyOfRange(sentences, 0, keep));
        }
        if (text.length() <= MAX_COMPARISON) {
            return text;
        }
        int stop = text.lastIndexOf(". ", MAX_COMPARISON);
        if (stop > 40) {
            return text.substring(0, stop + 1);
        }
        // No sentence end to cut at: stop at a word rather than mid-word.
        String cut = text.substring(0, MAX_COMPARISON);
        int space = cut.lastIndexOf(' ');
        return (space > 40 ? cut.substring(0, space) : cut).strip() + "…";
    }

    /** True when the line shares any real word with the tender. */
    private static boolean mentions(String line, Set<String> tenderWords) {
        Set<String> line_ = words(line);
        // A tender with almost no text of its own cannot vouch for anything; say so by
        // keeping the line rather than dropping every match on a thin notice.
        return tenderWords.size() < 5 || line_.stream().anyMatch(tenderWords::contains);
    }

    private static boolean namesTheCompany(String line, CapabilityProfile profile) {
        String normalised = normalise(line);
        if (profile.getOrgName() != null && !profile.getOrgName().isBlank()
                && normalised.contains(normalise(profile.getOrgName()))) {
            return true;
        }
        return nz(profile.getServices()).stream()
                .map(MatchComparisonValidator::normalise)
                .anyMatch(s -> s.length() >= 12 && normalised.contains(s));
    }

    /**
     * The two sides of a match line, split at the LAST separator.
     *
     * <p>Not the first: tender titles carry their own dashes -- "National Consultant -
     * Digital Transformation Specialist" -- and splitting there leaves a stub that makes
     * every later check wrong. The separator the model adds comes after the whole ask.
     * A dash inside a word ("e-Governance") never matches, since the pattern needs spaces.
     */
    private static String[] halves(String line) {
        Matcher m = SEPARATOR.matcher(line);
        int start = -1;
        int end = -1;
        while (m.find()) {
            start = m.start();
            end = m.end();
        }
        return start < 0 ? null : new String[]{line.substring(0, start), line.substring(end)};
    }

    /** True when the gap restates something already kept as a match. */
    private static boolean alreadyMatched(String gap, List<String> matches) {
        Set<String> words = words(gap);
        if (words.isEmpty()) {
            return false;
        }
        for (String match : matches) {
            String[] halves = halves(match);
            Set<String> asked = words(halves == null ? match : halves[0]);
            long shared = words.stream().filter(asked::contains).count();
            if ((double) shared / words.size() >= CONTRADICTS_A_MATCH) {
                return true;
            }
        }
        return false;
    }

    /** A gap whose words are almost all the profile's own is not a gap. */
    private static boolean covered(String line, Set<String> profileWords) {
        Set<String> line_ = words(line);
        if (line_.isEmpty() || profileWords.isEmpty()) {
            return false;
        }
        long shared = line_.stream().filter(profileWords::contains).count();
        return (double) shared / line_.size() >= COVERED_BY_PROFILE;
    }

    private static String tenderText(Tender t) {
        StringBuilder sb = new StringBuilder();
        sb.append(nz(t.getTitle())).append(' ').append(nz(t.getAiShortTitle())).append(' ')
                .append(nz(t.getAiSummary())).append(' ').append(nz(t.getDescription())).append(' ')
                .append(nz(t.getEligibilityText())).append(' ').append(nz(t.getBuyer()));
        if (t.getAiDeliverables() != null) {
            sb.append(' ').append(String.join(" ", t.getAiDeliverables()));
        }
        if (t.getAiCertifications() != null) {
            sb.append(' ').append(String.join(" ", t.getAiCertifications()));
        }
        return sb.toString();
    }

    private static String profileText(CapabilityProfile p) {
        return String.join(" ", nz(p.getSummary()), String.join(" ", nz(p.getServices())),
                nz(p.getPastProjects()).stream().map(x -> nz(x.getTitle())).collect(Collectors.joining(" ")));
    }

    private static Set<String> words(String text) {
        return NON_WORD.splitAsStream(text.toLowerCase(Locale.ROOT))
                .filter(w -> w.length() > 2 && !STOP.contains(w))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalise(String text) {
        return NON_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> nz(List<T> values) {
        return values == null ? List.of() : values;
    }
}
