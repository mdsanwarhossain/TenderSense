package com.bracit.tendersense.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the local model read out of one tender. Every field is optional: null (or an
 * empty list) means the notice did not say, or the validator rejected the model's answer.
 *
 * @param shortTitle         only asked for when the portal's title is too long to scan
 * @param summary            one or two plain sentences: what the buyer wants
 * @param deliverables       up to five things the supplier must deliver or do
 * @param location           where the work happens, when the notice names a place
 * @param minTurnoverBdt     minimum annual turnover required
 * @param minExperienceYears minimum years of experience required
 * @param certifications     certificates or licences a bidder must hold
 */
public record TenderEnrichment(
        String shortTitle,
        String summary,
        List<String> deliverables,
        String location,
        BigDecimal minTurnoverBdt,
        Integer minExperienceYears,
        List<String> certifications) {

    public TenderEnrichment {
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
    }

    public static TenderEnrichment empty() {
        return new TenderEnrichment(null, null, List.of(), null, null, null, List.of());
    }
}
