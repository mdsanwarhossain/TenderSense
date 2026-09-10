package com.bracit.tendersense.controller;

import com.bracit.tendersense.dto.*;
import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.*;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.EligibilityVerdictRepository;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.TenderTrackingService;
import com.bracit.tendersense.util.TenderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shortlist and tender detail.
 *
 * <p>Match and eligibility data are served from persisted results where they exist
 * and returned as null/empty otherwise, so the frontend can be built against the
 * real contract before the scoring services land.
 */
@RestController
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
@Slf4j
public class TenderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TenderRepository tenderRepository;
    private final MatchResultRepository matchResultRepository;
    private final EligibilityVerdictRepository eligibilityRepository;
    private final TenderMapper mapper;
    private final TenderTrackingService trackingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public PageResponse<TenderSummaryResponse> list(
            @CurrentOrganisation Organisation organisation,
            @RequestParam(required = false) MatchGrade grade,
            @RequestParam(required = false) SourcePortal source,
            @RequestParam(required = false) Sector sector,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(required = false) TrackingFilter tracked,
            @RequestParam(defaultValue = "false") boolean closingSoon,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        // A tender you bid on will usually be past its deadline by the time you look it up,
        // so "Submitted" would be near-empty if it honoured the closed-tender default.
        boolean closed = includeClosed || tracked == TrackingFilter.SUBMITTED;

        Page<MatchResult> ranked =
                matchResultRepository.findRanked(MatcherType.EMBEDDING, organisation.getId(),
                        grade, source, sector, closed,
                        tracked == TrackingFilter.SAVED, tracked == TrackingFilter.SUBMITTED,
                        closingSoon, TenderMapper.urgentFrom(), TenderMapper.urgentUntil(),
                        java.time.LocalDateTime.now(), pageable);

        // m.getTender() is a lazy proxy: reading its id is safe, reading its fields
        // outside the transaction is not. Load the real rows in one query instead.
        List<Long> tenderIds = ranked.getContent().stream()
                .map(m -> m.getTender().getId())
                .toList();
        Map<Long, Tender> tenders = new HashMap<>();
        tenderRepository.findAllById(tenderIds).forEach(t -> tenders.put(t.getId(), t));

        Map<Long, EligibilityVerdictView> verdicts =
                verdictsFor(organisation, List.copyOf(tenders.values()));
        Map<Long, TrackingState> tracking = trackingService.statesFor(organisation, tenderIds);

        List<TenderSummaryResponse> rows = ranked.getContent().stream()
                .map(m -> {
                    Tender tender = tenders.get(m.getTender().getId());
                    return tender == null ? null
                            : mapper.toSummary(tender, m, verdicts.get(tender.getId()),
                                    tracking.get(tender.getId()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return PageResponse.of(rows, ranked.getNumber(), ranked.getSize(),
                ranked.getTotalElements());
    }

    /**
     * Counts for the summary cards, taken in the database -- not from the page of rows the
     * browser holds.
     *
     * <p>Every card but "Total tenders" is also a filter. So the counts follow only the
     * scope controls -- the source dropdown and Include closed -- and never the card
     * filters: clicking "Saved" narrows the table; it does not make the S-grade card start
     * counting only saved tenders.
     */
    @GetMapping("/summary")
    public TenderListSummary summary(
            @CurrentOrganisation Organisation organisation,
            @RequestParam(required = false) SourcePortal source,
            @RequestParam(required = false) Sector sector,
            @RequestParam(defaultValue = "false") boolean includeClosed) {

        Long org = organisation.getId();
        LocalDateTime now = LocalDateTime.now();
        return new TenderListSummary(
                count(org, null, source, sector, includeClosed, false, false, false, now),
                count(org, MatchGrade.S, source, sector, includeClosed, false, false, false, now),
                count(org, null, source, sector, includeClosed, false, false, true, now),
                count(org, null, source, sector, includeClosed, true, false, false, now),
                // A bid you submitted is usually past its deadline, so this one always
                // includes closed tenders -- exactly as the Submitted filter does.
                count(org, null, source, sector, true, false, true, false, now));
    }

    /** One-row page of the list query: only the count query behind it matters. */
    private long count(Long organisationId, MatchGrade grade, SourcePortal source, Sector sector,
                       boolean includeClosed, boolean saved, boolean submitted, boolean closingSoon,
                       LocalDateTime now) {
        return matchResultRepository.findRanked(MatcherType.EMBEDDING, organisationId, grade,
                        source, sector, includeClosed, saved, submitted, closingSoon,
                        TenderMapper.urgentFrom(), TenderMapper.urgentUntil(), now,
                        PageRequest.of(0, 1))
                .getTotalElements();
    }

    /** Carries this company's save / submit marks, so the page shows the same buttons as the list. */
    @GetMapping("/{id}")
    public TenderDetailResponse detail(@CurrentOrganisation Organisation organisation,
                                       @PathVariable Long id) {
        Tender tender = require(id);
        TrackingState tracking = trackingService.statesFor(organisation, List.of(id)).get(id);
        return mapper.toDetail(tender, tracking);
    }

    @GetMapping("/{id}/evidence")
    public MatchEvidenceResponse evidence(@CurrentOrganisation Organisation organisation,
                                          @PathVariable Long id) {
        require(id);
        Optional<MatchResult> match = matchResultRepository
                .findByTenderIdAndOrganisationIdAndMatcherType(
                        id, organisation.getId(), MatcherType.EMBEDDING);
        // Use the projection, never findByTenderId().getGaps(): the gaps collection
        // is lazy and this method serialises after the transaction has closed.
        EligibilityVerdictView view = verdictViewFor(organisation, id);

        return match.map(m -> new MatchEvidenceResponse(
                        id, m.getGrade(), m.getScore(), m.getModelVersion(),
                        m.getSummaryText(), mapper.recommend(m, view),
                        m.getExclusionText(), m.getExclusionPenalty(),
                        readEvidence(m.getEvidenceJson())))
                .orElseGet(() -> new MatchEvidenceResponse(id, null, 0d, null,
                        "Not yet scored.", null, null, null, List.of()));
    }

    @GetMapping("/{id}/eligibility")
    public EligibilityGapResponse eligibility(@CurrentOrganisation Organisation organisation,
                                             @PathVariable Long id) {
        require(id);
        return eligibilityRepository.findByTenderIdWithGaps(id, organisation.getId())
                .map(v -> new EligibilityGapResponse(
                        id, v.getStatus(), v.getRulesApplied(),
                        v.getGaps().stream()
                                .map(g -> new EligibilityGapResponse.Gap(
                                        g.getRuleCode(), g.getRequirement(), g.getActual(),
                                        g.isBlocking(), g.getMessage()))
                                .toList()))
                .orElseGet(() -> new EligibilityGapResponse(
                        id, EligibilityStatus.NEEDS_VERIFICATION, null, List.of()));
    }

    /** Evidence is stored as JSON so the shape can evolve without a schema change. */
    private List<MatchEvidenceResponse.EvidencePair> readEvidence(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<MatchEvidenceResponse.EvidencePair>>() {
                    });
        } catch (Exception e) {
            log.warn("unreadable evidence json: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reads status and blocking-gap count via a projection. Touching the gaps
     * collection here would trigger lazy loading outside the transaction.
     */
    /** Single-tender projection, built on the same query the list endpoint uses. */
    private EligibilityVerdictView verdictViewFor(Organisation organisation, Long tenderId) {
        for (Object[] row : eligibilityRepository.findSummariesByTenderIds(
                List.of(tenderId), organisation.getId())) {
            return new EligibilityVerdictView((EligibilityStatus) row[1], ((Number) row[2]).intValue());
        }
        return null;
    }

    private Map<Long, EligibilityVerdictView> verdictsFor(Organisation organisation,
                                                          List<Tender> tenders) {
        if (tenders.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = tenders.stream().map(Tender::getId).toList();
        Map<Long, EligibilityVerdictView> out = new HashMap<>();
        for (Object[] row : eligibilityRepository.findSummariesByTenderIds(
                ids, organisation.getId())) {
            out.put(((Number) row[0]).longValue(),
                    new EligibilityVerdictView((EligibilityStatus) row[1],
                            ((Number) row[2]).intValue()));
        }
        return out;
    }

    private Tender require(Long id) {
        return tenderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("tender " + id + " not found"));
    }
}
