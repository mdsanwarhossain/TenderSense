package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.RuleOutcome;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Certification;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.service.EligibilityRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Certifications the tender demands, against those BracIT holds and still has valid. */
@Component
public class CertificationRule implements EligibilityRule {

    public static final String CODE = "CERTIFICATION";

    /**
     * Certifications procurement notices actually name, with the spelling variants
     * they use. Matching on the normalised code avoids missing "ISO/IEC 27001"
     * because the profile records "ISO27001".
     */
    private static final Map<String, Pattern> RECOGNISED = Map.of(
            "ISO27001", Pattern.compile("iso[\\s/:-]*(?:iec[\\s/:-]*)?27001", Pattern.CASE_INSENSITIVE),
            "ISO9001", Pattern.compile("iso[\\s/:-]*9001", Pattern.CASE_INSENSITIVE),
            "ISO20000", Pattern.compile("iso[\\s/:-]*(?:iec[\\s/:-]*)?20000", Pattern.CASE_INSENSITIVE),
            "CMMI3", Pattern.compile("cmmi(?:[\\s-]*(?:dev|development))?[\\s-]*(?:level[\\s-]*)?[35]",
                    Pattern.CASE_INSENSITIVE));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public RuleOutcome evaluate(Tender tender, CapabilityProfile profile) {
        String text = tender.getEligibilityText();
        if (text == null || text.isBlank()) {
            return RuleOutcome.unknown(CODE, "Tender states no eligibility text; verify manually.");
        }

        List<String> demanded = RECOGNISED.entrySet().stream()
                .filter(e -> e.getValue().matcher(text).find())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (demanded.isEmpty()) {
            return RuleOutcome.notApplicable(CODE);
        }

        Set<String> held = new HashSet<>();
        for (Certification c : profile.getCertifications()) {
            boolean expired = c.getValidUntil() != null && c.getValidUntil().isBefore(LocalDate.now());
            if (!expired && c.getCode() != null) {
                held.add(c.getCode().toUpperCase(Locale.ENGLISH));
            }
        }

        List<String> missing = demanded.stream().filter(d -> !held.contains(d)).toList();
        String requirement = "Requires " + String.join(", ", demanded);
        String actual = held.isEmpty() ? "BracIT holds none of these"
                : "BracIT holds " + String.join(", ", new TreeSet<>(held));

        if (missing.isEmpty()) {
            return RuleOutcome.pass(CODE, requirement, actual, "All required certifications held.");
        }
        return RuleOutcome.fail(CODE, requirement, actual,
                "Missing " + String.join(", ", missing)
                        + ". Would qualify once obtained or if an equivalent is accepted.");
    }
}
