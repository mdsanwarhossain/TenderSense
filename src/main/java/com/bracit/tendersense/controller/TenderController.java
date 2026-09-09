package com.bracit.tendersense.controller;

import com.bracit.tendersense.dto.*;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.*;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.EligibilityVerdictRepository;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.util.TenderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public PageResponse<TenderSummaryResponse> list(
            @RequestParam(required = false) MatchGrade grade,
            @RequestParam(required = false) SourcePortal source,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        Page<MatchResult> ranked =
                matchResultRepository.findRanked(MatcherType.EMBEDDING, grade, source,
                        includeClosed, java.time.LocalDateTime.now(), pageable);

        // m.getTender() is a lazy proxy: reading its id is safe, reading its fields
        // outside the transaction is not. Load the real rows in one query instead.
        List<Long> tenderIds = ranked.getContent().stream()
                .map(m -> m.getTender().getId())
                .toList();
        Map<Long, Tender> tenders = new HashMap<>();
        tenderRepository.findAllById(tenderIds).forEach(t -> tenders.put(t.getId(), t));

        Map<Long, EligibilityVerdictView> verdicts = verdictsFor(List.copyOf(tenders.values()));

        List<TenderSummaryResponse> rows = ranked.getContent().stream()
                .map(m -> {
                    Tender tender = tenders.get(m.getTender().getId());
                    return tender == null ? null
                            : mapper.toSummary(tender, m, verdicts.get(tender.getId()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return PageResponse.of(rows, ranked.getNumber(), ranked.getSize(),
                ranked.getTotalElements());
    }

    @GetMapping("/{id}")
    public TenderDetailResponse detail(@PathVariable Long id) {
        return mapper.toDetail(require(id));
    }

    @GetMapping("/{id}/evidence")
    public MatchEvidenceResponse evidence(@PathVariable Long id) {
        require(id);
        Optional<MatchResult> match =
                matchResultRepository.findByTenderIdAndMatcherType(id, MatcherType.EMBEDDING);
        // Use the projection, never findByTenderId().getGaps(): the gaps collection
        // is lazy and this method serialises after the transaction has closed.
        EligibilityVerdictView view = verdictViewFor(id);

        return match.map(m -> new MatchEvidenceResponse(
                        id, m.getGrade(), m.getScore(), m.getModelVersion(),
                        m.getSummaryText(), mapper.recommend(m, view),
                        readEvidence(m.getEvidenceJson())))
                .orElseGet(() -> new MatchEvidenceResponse(id, null, 0d, null,
                        "Not yet scored.", null, List.of()));
    }

    @GetMapping("/{id}/eligibility")
    public EligibilityGapResponse eligibility(@PathVariable Long id) {
        require(id);
        return eligibilityRepository.findByTenderIdWithGaps(id)
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
    private EligibilityVerdictView verdictViewFor(Long tenderId) {
        for (Object[] row : eligibilityRepository.findSummariesByTenderIds(List.of(tenderId))) {
            return new EligibilityVerdictView((EligibilityStatus) row[1], ((Number) row[2]).intValue());
        }
        return null;
    }

    private Map<Long, EligibilityVerdictView> verdictsFor(List<Tender> tenders) {
        if (tenders.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = tenders.stream().map(Tender::getId).toList();
        Map<Long, EligibilityVerdictView> out = new HashMap<>();
        for (Object[] row : eligibilityRepository.findSummariesByTenderIds(ids)) {
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
