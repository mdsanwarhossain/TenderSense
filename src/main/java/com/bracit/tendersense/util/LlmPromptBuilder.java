package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.PastProject;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.Sector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the prompts for the second-stage LLM review. Pure functions of their inputs, so
 * the exact text a model sees is unit-testable.
 *
 * <p>The split between the two prompts is load-bearing. Everything about the company goes
 * in the system prompt, which is therefore byte-identical for every tender of that company:
 * a local model server can reuse its cached prefix and only process the short tender part
 * on each call. On CPU that is most of the difference between a usable and an unusable run.
 */
public final class LlmPromptBuilder {

    /** Part of the review fingerprint. Change it whenever the wording below changes. */
    public static final String PROMPT_VERSION = "p1";

    static final int MAX_DESCRIPTION = 3000;
    static final int MAX_ELIGIBILITY = 1500;
    static final int MAX_CATEGORY = 300;
    static final int MAX_PROJECT = 220;

    private LlmPromptBuilder() {
    }

    public static String systemPrompt(Organisation organisation, CapabilityProfile profile) {
        String name = firstNonBlank(profile.getOrgName(), organisation.getName());
        StringBuilder sb = new StringBuilder(4096);

        sb.append("You are a bid manager at ").append(name).append(", deciding which public ")
          .append("tenders are worth the company's time. You will be shown one tender at a time. ")
          .append("Judge how well it fits the company described below, and reply with a score ")
          .append("from 0 to 100 and a short reason.\n\n");

        // Explicit bands: without them a small model clusters nearly everything at 70-85,
        // and a 0-100 scale with three values in use carries no information.
        sb.append("Scoring rubric:\n")
          .append("90-100  Core business: the main deliverable is what the company does, and it has ")
          .append("delivered closely similar work (see past projects).\n")
          .append("70-89   Strong fit: the main deliverable is clearly one of the company's services.\n")
          .append("40-69   Partial fit: some of the work matches, but a major part is outside its services.\n")
          .append("10-39   Weak fit: only a minor or incidental overlap.\n")
          .append("0-9     Out of scope, or the main work is listed under \"Work the company does not do\".\n\n");

        sb.append("Rules:\n")
          .append("- Judge the main deliverable, not incidental words. A building contract that ")
          .append("mentions a computer room is construction.\n")
          .append("- If the tender's main work matches an item under \"Work the company does not do\", ")
          .append("score 0-9 even when some wording overlaps with a service.\n")
          .append("- Score fit only. Do not guess about eligibility, turnover or certificates.\n")
          .append("- The reasoning must name the specific service, past project or exclusion that ")
          .append("decided the score, in at most two sentences.\n\n");

        sb.append("COMPANY: ").append(name).append('\n');
        List<Sector> sectors = organisation.getSectors();
        if (sectors != null && !sectors.isEmpty()) {
            sb.append("Sectors: ").append(sectors.stream().map(LlmPromptBuilder::label)
                    .collect(Collectors.joining(", "))).append('\n');
        }
        if (notBlank(profile.getSummary())) {
            sb.append("Summary: ").append(clean(profile.getSummary())).append('\n');
        }

        sb.append("\nServices the company offers:\n");
        bullets(sb, profile.getServices());

        List<PastProject> projects = profile.getPastProjects();
        if (projects != null && !projects.isEmpty()) {
            sb.append("\nPast projects:\n");
            for (PastProject p : projects) {
                sb.append("- ").append(clean(p.getTitle()));
                String meta = List.of(nullToEmpty(p.getClient()),
                                p.getYear() == null ? "" : String.valueOf(p.getYear()))
                        .stream().filter(LlmPromptBuilder::notBlank).collect(Collectors.joining(", "));
                if (!meta.isEmpty()) {
                    sb.append(" (").append(clean(meta)).append(')');
                }
                if (notBlank(p.getDescription())) {
                    sb.append(": ").append(truncate(clean(p.getDescription()), MAX_PROJECT));
                }
                sb.append('\n');
            }
        }

        sb.append("\nWork the company does not do:\n");
        List<String> exclusions = profile.getExclusions();
        if (exclusions == null || exclusions.isEmpty()) {
            sb.append("- (none listed)\n");
        } else {
            bullets(sb, exclusions);
        }
        return sb.toString();
    }

    public static String tenderPrompt(Tender tender) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("TENDER\n");
        line(sb, "Title", tender.getTitle(), 600);
        line(sb, "Procuring entity", tender.getProcuringEntity(), 300);
        line(sb, "Procurement nature", tender.getProcurementNature(), 100);
        line(sb, "Procurement method", tender.getProcurementMethod(), 100);
        line(sb, "Category", firstNonBlank(tender.getCpvTop(), tender.getCpvRaw()), MAX_CATEGORY);
        line(sb, "Description", tender.getDescription(), MAX_DESCRIPTION);
        line(sb, "Eligibility", tender.getEligibilityText(), MAX_ELIGIBILITY);
        return sb.toString();
    }

    /**
     * Identifies everything a verdict depends on. A stored verdict whose fingerprint no
     * longer matches is stale: the profile was edited, the tender was revised, or the
     * model or prompt changed. One rule covers all four.
     */
    public static String fingerprint(Long tenderId, String tenderContentHash,
                                     Instant profileUpdatedAt, String modelVersion) {
        String input = String.join("|",
                String.valueOf(tenderId),
                tenderContentHash == null ? "-" : tenderContentHash,
                profileUpdatedAt == null ? "-" : profileUpdatedAt.toString(),
                modelVersion,
                PROMPT_VERSION);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    // ---- helpers ----

    private static void bullets(StringBuilder sb, List<String> items) {
        if (items == null || items.isEmpty()) {
            sb.append("- (none listed)\n");
            return;
        }
        for (String item : items) {
            if (notBlank(item)) {
                sb.append("- ").append(clean(item)).append('\n');
            }
        }
    }

    private static void line(StringBuilder sb, String label, String value, int max) {
        if (notBlank(value)) {
            sb.append(label).append(": ").append(truncate(clean(value), max)).append('\n');
        }
    }

    /** IT_SERVICES reads as noise to a model and to a person; "IT services" does not. */
    static String label(Sector sector) {
        String[] words = sector.name().toLowerCase(Locale.ROOT).split("_");
        words[0] = words[0].equals("it") ? "IT"
                : Character.toUpperCase(words[0].charAt(0)) + words[0].substring(1);
        return String.join(" ", words);
    }

    static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip();
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).stripTrailing() + "…";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : (b == null ? "" : b);
    }
}
