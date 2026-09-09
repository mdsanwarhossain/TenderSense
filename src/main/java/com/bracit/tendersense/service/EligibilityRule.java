package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.RuleOutcome;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Tender;

/**
 * A single hard eligibility check.
 *
 * <p>Rules are fixed logic, never AI. Eligibility is a yes/no compliance question:
 * a probabilistic answer to "are we allowed to bid" is worse than no answer, because
 * it is confidently wrong some of the time and cannot be audited.
 */
public interface EligibilityRule {

    String code();

    RuleOutcome evaluate(Tender tender, CapabilityProfile profile);
}
