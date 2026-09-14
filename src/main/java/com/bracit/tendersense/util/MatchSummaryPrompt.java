package com.bracit.tendersense.util;

import com.bracit.tendersense.dto.MatchEvidenceResponse.EvidencePair;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Tender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The prompt and output schema for comparing one tender with one company's profile.
 *
 * <p>What the bid team wants to read is not "why did this score 62%" but "what does this
 * tender need, and which of our work covers it". So the model is given both sides and the
 * overlaps the matcher already found, and is asked to name both sides in every point.
 *
 * <p>Two rules exist because a first draft broke them on real data: a "match" that only
 * echoes a service name says nothing about the tender, and the model listed the tender's
 * own deliverables as gaps -- which reads as "this company cannot do the thing it is
 * bidding for".
 *
 * <p>The small model broke both again when it was only told them in prose: every match
 * line came back naming the same phrase twice, and every gap was a second comparison
 * sentence. So the rules are shown as a worked example rather than described, and what
 * the model returns is checked line by line afterwards by
 * {@link MatchComparisonValidator} -- a rule this prompt cannot enforce is enforced there.
 */
public final class MatchSummaryPrompt {

    /** Stored with each summary: bumping it regenerates every cached comparison. */
    public static final String VERSION = "compare-2";

    public static final String SYSTEM = """
            You compare one tender with one company's capability profile, for that company's \
            own bid team. You are given both, and the overlaps a matching system already found.

            Use only what the two texts say. Never credit the company with experience its \
            profile does not show, and never invent a requirement the tender does not state.

            comparison: two plain sentences. The first says what the tender needs. The \
            second says how far this company's stated work covers it. No sales language, \
            no score, no recommendation.

            matches: up to 4 lines, each "<what the TENDER asks for> - <the COMPANY work \
            that covers it>". The left side must come from the tender text and the right \
            side from the company text. They must be different things: repeating one \
            phrase on both sides is wrong.

            gaps: up to 3 lines. Each is ONE short thing the tender asks for that the \
            company text does not show. A gap is not a comparison: no dash, no mention of \
            what the company does have. Return an empty list when the profile covers the tender.

            Worked example.
            TENDER: Supply and install 40 solar irrigation pumps in Barisal, with five \
            years of maintenance and operator training.
            COMPANY: Solar pump supply and installation. Rural electrification projects.
            Correct answer:
            comparison: "The tender needs 40 solar irrigation pumps installed in Barisal, \
            then maintained for five years and operators trained. The company installs \
            solar pumps and works on rural electrification, but its profile says nothing \
            about long maintenance contracts or training."
            matches: ["Install 40 solar irrigation pumps - solar pump supply and installation"]
            gaps: ["Five-year maintenance contract", "Operator training"]
            Note what makes it correct: the match names the tender's ask on the left and \
            the company's own words on the right, and each gap is a single missing thing \
            with no dash and no mention of the company.""";

    private static final int MAX_TENDER_TEXT = 2500;
    private static final int MAX_PROFILE_TEXT = 900;
    private static final int MAX_SERVICES = 25;
    private static final int MAX_PROJECTS = 6;
    private static final int MAX_EVIDENCE = 4;

    private MatchSummaryPrompt() {
    }

    public static String user(Tender tender, CapabilityProfile profile, List<EvidencePair> evidence) {
        StringBuilder sb = new StringBuilder();

        sb.append("TENDER\n");
        sb.append("Title: ").append(nullToEmpty(firstNonBlank(tender.getAiShortTitle(), tender.getTitle()))).append('\n');
        sb.append("What it needs: ")
                .append(cut(firstNonBlank(tender.getAiSummary(), tender.getDescription()), MAX_TENDER_TEXT)).append('\n');
        if (tender.getAiDeliverables() != null && tender.getAiDeliverables().length > 0) {
            sb.append("Deliverables: ").append(String.join("; ", tender.getAiDeliverables())).append('\n');
        }
        String requirements = requirements(tender);
        if (!requirements.isEmpty()) {
            sb.append("Requirements: ").append(requirements).append('\n');
        }
        if (tender.getBuyer() != null) {
            sb.append("Buyer: ").append(tender.getBuyer()).append('\n');
        }

        sb.append("\nCOMPANY: ").append(nullToEmpty(profile.getOrgName())).append('\n');
        sb.append("About: ").append(cut(profile.getSummary(), MAX_PROFILE_TEXT)).append('\n');
        sb.append("Services: ").append(join(profile.getServices(), MAX_SERVICES)).append('\n');
        if (!profile.getPastProjects().isEmpty()) {
            sb.append("Past work: ").append(profile.getPastProjects().stream()
                    .limit(MAX_PROJECTS)
                    .map(p -> p.getTitle() + (p.getClient() == null ? "" : " for " + p.getClient())
                            + (p.getYear() == null ? "" : " (" + p.getYear() + ")"))
                    .reduce((a, b) -> a + "; " + b).orElse("")).append('\n');
        }
        if (!profile.getCertifications().isEmpty()) {
            sb.append("Certifications: ").append(profile.getCertifications().stream()
                    .map(c -> c.getCode() == null ? c.getName() : c.getCode())
                    .reduce((a, b) -> a + ", " + b).orElse("")).append('\n');
        }

        if (evidence != null && !evidence.isEmpty()) {
            sb.append("\nOVERLAPS THE MATCHER FOUND (company work | tender text):\n");
            evidence.stream().limit(MAX_EVIDENCE).forEach(e ->
                    sb.append("- ").append(cut(e.profileText(), 160))
                            .append(" | ").append(cut(e.tenderText(), 240)).append('\n'));
        }
        return sb.toString();
    }

    /** Length limits are enforced while the model writes, so a looping answer still parses. */
    public static Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("comparison", text(420));
        props.put("matches", Map.of("type", "array", "items", text(160), "maxItems", 4));
        props.put("gaps", Map.of("type", "array", "items", text(160), "maxItems", 3));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", new ArrayList<>(props.keySet()));
        return schema;
    }

    private static String requirements(Tender t) {
        List<String> out = new ArrayList<>();
        if (t.getAiMinTurnoverBdt() != null) {
            out.add("minimum annual turnover BDT " + t.getAiMinTurnoverBdt().toPlainString());
        }
        if (t.getAiMinExperienceYears() != null) {
            out.add("at least " + t.getAiMinExperienceYears() + " years of experience");
        }
        if (t.getAiCertifications() != null && t.getAiCertifications().length > 0) {
            out.add("certificates: " + String.join(", ", t.getAiCertifications()));
        }
        return String.join("; ", out);
    }

    private static Map<String, Object> text(int maxLength) {
        return Map.of("type", "string", "maxLength", maxLength);
    }

    private static String join(List<String> values, int limit) {
        return values == null ? "" : values.stream().limit(limit).reduce((a, b) -> a + "; " + b).orElse("");
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String cut(String s, int max) {
        String v = nullToEmpty(s).strip();
        return v.length() <= max ? v : v.substring(0, max) + " …";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
