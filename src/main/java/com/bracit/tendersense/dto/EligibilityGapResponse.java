package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.EligibilityStatus;

import java.util.List;

/**
 * Rules-based verdict. NEEDS_VERIFICATION is a first-class outcome: roughly half
 * of e-GP tenders state eligibility as "As per TDS" rather than in machine-readable
 * terms, and claiming a pass/fail on those would be dishonest.
 */
public record EligibilityGapResponse(
        Long tenderId,
        EligibilityStatus status,
        String rulesApplied,
        List<Gap> gaps) {

    public record Gap(String ruleCode,
                      String requirement,
                      String actual,
                      boolean blocking,
                      String message) {
    }
}
