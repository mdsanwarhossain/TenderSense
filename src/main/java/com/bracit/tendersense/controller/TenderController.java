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
import com.bracit.tendersense.service.ShortlistService;
import com.bracit.tendersense.service.TenderTrackingService;
import com.bracit.tendersense.util.TenderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    private final ShortlistService shortlistService;
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
        return shortlistService.list(organisation,
                new ShortlistFilter(grade, source, sector, includeClosed, tracked, closingSoon),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
    }

    /**
     * Counts for the summary cards, taken in the database -- not from the page of rows the
     * browser holds. See {@link ShortlistService#summary} for which filters they follow.
     */
    @GetMapping("/summary")
    public TenderListSummary summary(
            @CurrentOrganisation Organisation organisation,
            @RequestParam(required = false) SourcePortal source,
            @RequestParam(required = false) Sector sector,
            @RequestParam(defaultValue = "false") boolean includeClosed) {
        return shortlistService.summary(organisation, source, sector, includeClosed);
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
     * Status and blocking-gap count via a projection. Touching the gaps collection here
     * would trigger lazy loading outside the transaction.
     */
    private EligibilityVerdictView verdictViewFor(Organisation organisation, Long tenderId) {
        for (Object[] row : eligibilityRepository.findSummariesByTenderIds(
                List.of(tenderId), organisation.getId())) {
            return new EligibilityVerdictView((EligibilityStatus) row[1], ((Number) row[2]).intValue());
        }
        return null;
    }

    private Tender require(Long id) {
        return tenderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("tender " + id + " not found"));
    }
}
