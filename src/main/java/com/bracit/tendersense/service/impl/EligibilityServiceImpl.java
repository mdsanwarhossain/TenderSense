package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.RuleOutcome;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.EligibilityGap;
import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.EligibilityStatus;
import com.bracit.tendersense.repository.EligibilityVerdictRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.EligibilityRule;
import com.bracit.tendersense.service.EligibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies every rule and aggregates the outcomes into one verdict.
 *
 * <p>Aggregation is deliberately conservative:
 * <ul>
 *   <li>any FAIL makes the tender INELIGIBLE, and that gap is blocking;</li>
 *   <li>any UNKNOWN, with no FAIL, makes it NEEDS_VERIFICATION -- recorded as a
 *       non-blocking gap so the BD team knows what to check;</li>
 *   <li>only when every applicable rule passes is it ELIGIBLE.</li>
 * </ul>
 *
 * <p>The middle case is not a cop-out. Roughly half of e-GP tenders state eligibility
 * as "As per TDS", and the honest answer for those is "a human must read the tender
 * document", not a manufactured pass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EligibilityServiceImpl implements EligibilityService {

    private final List<EligibilityRule> rules;
    private final CapabilityProfileService profileService;
    private final EligibilityVerdictRepository verdictRepository;

    @Override
    @Transactional
    public EligibilityVerdict evaluate(Tender tender) {
        return persist(tender, profileService.current());
    }

    @Override
    @Transactional
    public List<EligibilityVerdict> evaluateAll(List<Tender> tenders) {
        CapabilityProfile profile = profileService.current();
        List<EligibilityVerdict> out = new ArrayList<>(tenders.size());
        int eligible = 0, ineligible = 0, verify = 0;

        for (Tender tender : tenders) {
            EligibilityVerdict verdict = persist(tender, profile);
            out.add(verdict);
            switch (verdict.getStatus()) {
                case ELIGIBLE -> eligible++;
                case INELIGIBLE -> ineligible++;
                case NEEDS_VERIFICATION -> verify++;
            }
        }

        log.info("eligibility: {} eligible, {} ineligible, {} need verification",
                eligible, ineligible, verify);
        return out;
    }

    private EligibilityVerdict persist(Tender tender, CapabilityProfile profile) {
        List<RuleOutcome> outcomes = rules.stream()
                .map(rule -> safeEvaluate(rule, tender, profile))
                .toList();

        boolean anyFail = outcomes.stream().anyMatch(o -> o.result() == RuleOutcome.Result.FAIL);
        boolean anyUnknown = outcomes.stream().anyMatch(o -> o.result() == RuleOutcome.Result.UNKNOWN);

        EligibilityStatus status = anyFail ? EligibilityStatus.INELIGIBLE
                : anyUnknown ? EligibilityStatus.NEEDS_VERIFICATION
                : EligibilityStatus.ELIGIBLE;

        EligibilityVerdict verdict = verdictRepository.findByTenderId(tender.getId())
                .orElseGet(() -> EligibilityVerdict.builder().tender(tender).build());

        verdict.setStatus(status);
        verdict.setCheckedAt(Instant.now());
        verdict.setRulesApplied(String.join(",", rules.stream().map(EligibilityRule::code).toList()));
        verdict.getGaps().clear();

        for (RuleOutcome o : outcomes) {
            if (o.result() == RuleOutcome.Result.PASS
                    || o.result() == RuleOutcome.Result.NOT_APPLICABLE) {
                continue;
            }
            verdict.getGaps().add(EligibilityGap.builder()
                    .verdict(verdict)
                    .ruleCode(o.ruleCode())
                    .requirement(o.requirement())
                    .actual(o.actual())
                    // Only an outright FAIL blocks; an UNKNOWN is a prompt to check.
                    .blocking(o.result() == RuleOutcome.Result.FAIL)
                    .message(o.message())
                    .build());
        }

        return verdictRepository.save(verdict);
    }

    /** A broken rule must degrade to "verify manually", never to a false pass. */
    private RuleOutcome safeEvaluate(EligibilityRule rule, Tender tender, CapabilityProfile profile) {
        try {
            return rule.evaluate(tender, profile);
        } catch (Exception e) {
            log.warn("rule {} failed on tender {}: {}", rule.code(), tender.getId(), e.getMessage());
            return RuleOutcome.unknown(rule.code(),
                    "Rule could not be evaluated; verify manually.");
        }
    }
}
