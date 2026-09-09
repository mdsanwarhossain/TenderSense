package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.EligibilityGap;
import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.service.SummaryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic summaries built from the evidence the matcher actually produced.
 *
 * <p>This ships first and stays the default. It needs no API key, cannot hallucinate
 * a capability BracIT does not have, and produces identical text on every run --
 * which also makes the demo reproducible. The LLM writer is an upgrade to phrasing,
 * not to substance.
 */
@Service
@Primary
@ConditionalOnProperty(name = "tendersense.summary.enabled", havingValue = "false",
        matchIfMissing = true)
public class TemplateSummaryServiceImpl implements SummaryService {

    /** Below this similarity the evidence is too weak to quote as a reason. */
    private static final double EVIDENCE_FLOOR = 0.20;

    @Override
    public String summarise(Organisation organisation, Tender tender, ScoredMatch match,
                            EligibilityVerdict verdict) {
        StringBuilder sb = new StringBuilder();

        List<ScoredMatch.Evidence> strong = match.evidence().stream()
                .filter(e -> e.similarity() >= EVIDENCE_FLOOR)
                .toList();

        if (strong.isEmpty()) {
            sb.append("No strong overlap with BracIT's capability profile.");
        } else {
            sb.append("Matches ").append(organisation.getName()).append("'s ")
                    .append(strong.stream()
                            .map(e -> quote(e.profileText()))
                            .collect(Collectors.joining("; ")))
                    .append('.');
        }

        if (match.exclusion() != null) {
            sb.append(" Scored down: reads more like ")
              .append(quote(match.exclusion().text()))
              .append(" than like our work.");
        }

        if (verdict != null) {
            List<EligibilityGap> blocking = verdict.getGaps().stream()
                    .filter(EligibilityGap::isBlocking).toList();
            List<EligibilityGap> checks = verdict.getGaps().stream()
                    .filter(g -> !g.isBlocking()).toList();

            if (!blocking.isEmpty()) {
                sb.append(" Cannot bid: ")
                        .append(blocking.stream().map(EligibilityGap::getMessage)
                                .collect(Collectors.joining(" ")));
            } else if (!checks.isEmpty()) {
                sb.append(" Verify before bidding: ")
                        .append(checks.stream().map(EligibilityGap::getMessage)
                                .collect(Collectors.joining(" ")));
            } else {
                sb.append(" Meets all checked eligibility requirements.");
            }
        }

        Integer days = com.bracit.tendersense.util.TenderMapper.daysToDeadline(tender.getClosingAt());
        if (days != null && days >= 0 && days <= 7) {
            sb.append(" Closes in ").append(days).append(days == 1 ? " day." : " days.");
        }
        return sb.toString();
    }

    /** Capability statements can be long; the summary quotes a readable fragment. */
    private static String quote(String capability) {
        String c = capability.trim();
        int stop = c.indexOf('.');
        if (stop > 20) {
            c = c.substring(0, stop);
        }
        // Lower-casing the first letter reads naturally mid-sentence, but not for an
        // acronym: "IT outsourcing" must not become "iT outsourcing".
        boolean acronym = c.length() > 1 && Character.isUpperCase(c.charAt(1));
        String lowered = acronym || c.isEmpty()
                ? c
                : Character.toLowerCase(c.charAt(0)) + c.substring(1);
        return lowered.length() <= 90 ? lowered : lowered.substring(0, 90) + "...";
    }
}
