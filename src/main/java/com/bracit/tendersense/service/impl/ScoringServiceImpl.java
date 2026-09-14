package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final NotificationService notificationService;
    private final MatchResultRepository matchResultRepository;
    private final TenderRepository tenderRepository;
    private final OrganisationRepository organisationRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public int scoreAll(Organisation organisation, List<Tender> tenders) {
        if (tenders.isEmpty()) {
            return 0;
        }
        long started = System.currentTimeMillis();

        // The sector gate. Ingestion stores and classifies everything; only scoring is
        // restricted, so a mis-tagged tender is invisible to this company rather than
        // lost — fixing the tag surfaces it with no re-scrape.
        List<Tender> eligible = tenders.stream()
                .filter(t -> inSubscribedSectors(organisation, t))
                .toList();
        int gated = tenders.size() - eligible.size();
        if (eligible.isEmpty()) {
            log.info("{}: all {} tenders fall outside subscribed sectors",
                    organisation.getSlug(), gated);
            return 0;
        }
        tenders = eligible;

        Map<MatcherType, Map<Long, ScoredMatch>> byMatcher = new EnumMap<>(MatcherType.class);
        Map<MatcherType, String> versions = new EnumMap<>(MatcherType.class);
        for (MatchingService matcher : matchers) {
            byMatcher.put(matcher.type(), matcher.scoreAll(organisation, tenders));
            versions.put(matcher.type(), matcher.modelVersion());
        }

        List<EligibilityVerdict> verdicts = eligibilityService.evaluateAll(organisation, tenders);
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
                persist(organisation, tender, type, match, versions.get(type),
                        type == MatcherType.EMBEDDING ? verdictByTender.get(tender.getId()) : null);
                written++;
            }
        }

        // Grades are assigned by recalibrateGrades() once every score exists.
        long ms = System.currentTimeMillis() - started;
        log.info("{}: scored {} tenders ({} rows) in {}ms -- {}ms each, {} gated by sector",
                organisation.getSlug(), tenders.size(), written, ms,
                ms / Math.max(1, tenders.size()), gated);
        return written;
    }

    @Override
    @Transactional
    public void recalibrateGrades(Organisation organisation) {
        transactionTemplate.executeWithoutResult(status -> doRecalibrate(organisation));
    }

    private void doRecalibrate(Organisation organisation) {
        Long orgId = organisation.getId();
        List<Double> distribution =
                matchResultRepository.findScoresByMatcherType(MatcherType.EMBEDDING, orgId);
        GradeCalibrationService.Thresholds t = calibration.calibrate(organisation, distribution);

        for (MatcherType type : MatcherType.values()) {
            matchResultRepository.applyGrade(type, orgId, MatchGrade.S, t.s(), Double.MAX_VALUE);
            matchResultRepository.applyGrade(type, orgId, MatchGrade.A, t.a(), t.s());
            matchResultRepository.applyGrade(type, orgId, MatchGrade.B, t.b(), t.a());
            matchResultRepository.applyGrade(type, orgId, MatchGrade.C, -1d, t.b());
        }
        log.info("{}: grades rewritten across {} scored results using {}",
                organisation.getSlug(), distribution.size(), t.basis());

        // Grades are only final once every score has been rewritten above, so this is
        // the one place -- reached by scheduled discovery, reconcile, and a manual
        // rescore alike -- where "newly S/A-graded" can be answered correctly.
        notificationService.syncNewMatches(organisation);
    }

    /** True when the tender's sector is one this organisation subscribes to. */
    private boolean inSubscribedSectors(Organisation organisation, Tender tender) {
        // OTHER is scored for everyone: a classification miss must not silently withhold
        // a tender from every tenant.
        if (tender.getSector() == null || tender.getSector() == Sector.OTHER) {
            return true;
        }
        List<Sector> subscribed = organisation.getSectors();
        return subscribed.isEmpty() || subscribed.contains(tender.getSector());
    }

    @Override
    public int rescoreEverything(Organisation organisation) {
        // Deliberately not transactional at this level: each batch commits on its own,
        // so a failure late in a long run keeps the work already done and the whole
        // corpus is not locked for the duration.
        List<Tender> all = tenderRepository.findAll();
        int total = 0;
        for (int i = 0; i < all.size(); i += BATCH_SIZE) {
            total += scoreAll(organisation, all.subList(i, Math.min(i + BATCH_SIZE, all.size())));
            log.info("{}: rescore progress {}/{}", organisation.getSlug(),
                    Math.min(i + BATCH_SIZE, all.size()), all.size());
        }
        recalibrateGrades(organisation);
        return total;
    }

    @Override
    public int rescoreAllOrganisations() {
        int total = 0;
        for (Organisation org : organisationRepository.findByActiveTrueOrderByIdAsc()) {
            total += rescoreEverything(org);
        }
        return total;
    }

    private void persist(Organisation organisation, Tender tender, MatcherType type,
                         ScoredMatch match, String modelVersion, EligibilityVerdict verdict) {
        MatchResult result = matchResultRepository
                .findByTenderIdAndOrganisationIdAndMatcherType(
                        tender.getId(), organisation.getId(), type)
                .orElseGet(() -> MatchResult.builder()
                        .tender(tender).organisation(organisation).matcherType(type).build());

        result.setScore(match.score());
        result.setExclusionText(match.exclusion() == null ? null
                : truncate(match.exclusion().text()));
        result.setExclusionPenalty(match.exclusion() == null ? null
                : match.exclusion().penalty());
        result.setModelVersion(modelVersion);
        result.setComputedAt(Instant.now());
        result.setEvidenceJson(writeEvidence(match));

        // Only the semantic result carries the human-facing summary; duplicating it
        // on the keyword row would imply the baseline explains itself, which it cannot.
        if (type == MatcherType.EMBEDDING) {
            result.setSummaryText(summaryService.summarise(organisation, tender, match, verdict));
        }
        matchResultRepository.save(result);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 512 ? s : s.substring(0, 512);
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
