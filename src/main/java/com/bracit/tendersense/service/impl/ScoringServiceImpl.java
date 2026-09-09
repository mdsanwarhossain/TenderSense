package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Orchestrates one scoring pass: both matchers, calibration, eligibility, summaries.
 *
 * <p>Order matters. Grades cannot be assigned until the whole batch is scored, because
 * calibration is distribution-based -- so scoring runs first, thresholds are computed,
 * and only then are grades written.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringServiceImpl implements ScoringService {

    private static final int BATCH_SIZE = 500;

    private final List<MatchingService> matchers;
    private final GradeCalibrationService calibration;
    private final EligibilityService eligibilityService;
    private final SummaryService summaryService;
    private final MatchResultRepository matchResultRepository;
    private final TenderRepository tenderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public int scoreAll(List<Tender> tenders) {
        if (tenders.isEmpty()) {
            return 0;
        }
        long started = System.currentTimeMillis();

        Map<MatcherType, Map<Long, ScoredMatch>> byMatcher = new EnumMap<>(MatcherType.class);
        Map<MatcherType, String> versions = new EnumMap<>(MatcherType.class);
        for (MatchingService matcher : matchers) {
            byMatcher.put(matcher.type(), matcher.scoreAll(tenders));
            versions.put(matcher.type(), matcher.modelVersion());
        }

        List<EligibilityVerdict> verdicts = eligibilityService.evaluateAll(tenders);
        Map<Long, EligibilityVerdict> verdictByTender = new HashMap<>();
        for (EligibilityVerdict v : verdicts) {
            verdictByTender.put(v.getTender().getId(), v);
        }

        int written = 0;
        for (Tender tender : tenders) {
            for (MatcherType type : byMatcher.keySet()) {
                ScoredMatch match = byMatcher.get(type).get(tender.getId());
                if (match == null) {
                    continue;
                }
                persist(tender, type, match, versions.get(type),
                        type == MatcherType.EMBEDDING ? verdictByTender.get(tender.getId()) : null);
                written++;
            }
        }

        // Grades are assigned by recalibrateGrades() once every score exists.
        long ms = System.currentTimeMillis() - started;
        log.info("scored {} tenders ({} rows) in {}ms -- {}ms per tender",
                tenders.size(), written, ms, ms / Math.max(1, tenders.size()));
        return written;
    }

    @Override
    @Transactional
    public void recalibrateGrades() {
        List<Double> distribution =
                matchResultRepository.findScoresByMatcherType(MatcherType.EMBEDDING);
        GradeCalibrationService.Thresholds t = calibration.calibrate(distribution);

        for (MatcherType type : MatcherType.values()) {
            matchResultRepository.applyGrade(type, MatchGrade.S, t.s(), Double.MAX_VALUE);
            matchResultRepository.applyGrade(type, MatchGrade.A, t.a(), t.s());
            matchResultRepository.applyGrade(type, MatchGrade.B, t.b(), t.a());
            matchResultRepository.applyGrade(type, MatchGrade.C, -1d, t.b());
        }
        log.info("grades rewritten across {} scored results using {}",
                distribution.size(), t.basis());
    }

    @Override
    public int rescoreEverything() {
        // Deliberately not transactional at this level: each batch commits on its own,
        // so a failure late in a long run keeps the work already done and the whole
        // corpus is not locked for the duration.
        List<Tender> all = tenderRepository.findAll();
        int total = 0;
        for (int i = 0; i < all.size(); i += BATCH_SIZE) {
            total += scoreAll(all.subList(i, Math.min(i + BATCH_SIZE, all.size())));
            log.info("rescore progress: {}/{} tenders", Math.min(i + BATCH_SIZE, all.size()), all.size());
        }
        recalibrateGrades();
        return total;
    }

    private void persist(Tender tender, MatcherType type, ScoredMatch match,
                         String modelVersion, EligibilityVerdict verdict) {
        MatchResult result = matchResultRepository
                .findByTenderIdAndMatcherType(tender.getId(), type)
                .orElseGet(() -> MatchResult.builder().tender(tender).matcherType(type).build());

        result.setScore(match.score());
        result.setModelVersion(modelVersion);
        result.setComputedAt(Instant.now());
        result.setEvidenceJson(writeEvidence(match));

        // Only the semantic result carries the human-facing summary; duplicating it
        // on the keyword row would imply the baseline explains itself, which it cannot.
        if (type == MatcherType.EMBEDDING) {
            result.setSummaryText(summaryService.summarise(tender, match, verdict));
        }
        matchResultRepository.save(result);
    }

    private String writeEvidence(ScoredMatch match) {
        if (match.evidence().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(match.evidence());
        } catch (Exception e) {
            log.warn("could not serialise evidence: {}", e.getMessage());
            return null;
        }
    }
}
