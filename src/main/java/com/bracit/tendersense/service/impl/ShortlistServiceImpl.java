package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.enums.EligibilityVerdictView;
import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.ShortlistFilter;
import com.bracit.tendersense.dto.TenderListSummary;
import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.dto.TrackingState;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.EligibilityStatus;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.TrackingFilter;
import com.bracit.tendersense.repository.EligibilityVerdictRepository;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.ShortlistService;
import com.bracit.tendersense.service.TenderTrackingService;
import com.bracit.tendersense.util.TenderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShortlistServiceImpl implements ShortlistService {

    private final TenderRepository tenderRepository;
    private final MatchResultRepository matchResultRepository;
    private final EligibilityVerdictRepository eligibilityRepository;
    private final TenderMapper mapper;
    private final TenderTrackingService trackingService;

    @Override
    public PageResponse<TenderSummaryResponse> list(Organisation organisation, ShortlistFilter filter,
                                                    Pageable pageable) {
        Page<MatchResult> ranked = ranked(organisation.getId(), filter, pageable);

        // m.getTender() is a lazy proxy: reading its id is safe, reading its fields
        // outside the transaction is not. Load the real rows in one query instead.
        List<Long> tenderIds = ranked.getContent().stream()
                .map(m -> m.getTender().getId())
                .toList();
        Map<Long, Tender> tenders = new HashMap<>();
        tenderRepository.findAllById(tenderIds).forEach(t -> tenders.put(t.getId(), t));

        Map<Long, EligibilityVerdictView> verdicts = verdictsFor(organisation, tenderIds);
        Map<Long, TrackingState> tracking = trackingService.statesFor(organisation, tenderIds);

        List<TenderSummaryResponse> rows = ranked.getContent().stream()
                .map(m -> {
                    Tender tender = tenders.get(m.getTender().getId());
                    return tender == null ? null
                            : mapper.toSummary(tender, m, verdicts.get(tender.getId()),
                                    tracking.get(tender.getId()));
                })
                .filter(Objects::nonNull)
                .toList();

        return PageResponse.of(rows, ranked.getNumber(), ranked.getSize(), ranked.getTotalElements());
    }

    @Override
    public long count(Organisation organisation, ShortlistFilter filter) {
        // One-row page of the list query: only the count query behind it matters.
        return ranked(organisation.getId(), filter, PageRequest.of(0, 1)).getTotalElements();
    }

    @Override
    public TenderListSummary summary(Organisation organisation, SourcePortal source, Sector sector,
                                     boolean includeClosed) {
        ShortlistFilter scope = new ShortlistFilter(null, source, sector, includeClosed, null, false);
        return new TenderListSummary(
                count(organisation, scope),
                count(organisation, scope.withGrade(MatchGrade.S)),
                count(organisation, scope.withClosingSoon(true)),
                count(organisation, scope.withTracked(TrackingFilter.SAVED)),
                // Includes closed tenders whatever the toggle says -- exactly as the
                // Submitted filter does (ShortlistFilter.effectiveIncludeClosed).
                count(organisation, scope.withTracked(TrackingFilter.SUBMITTED)));
    }

    private Page<MatchResult> ranked(Long organisationId, ShortlistFilter f, Pageable pageable) {
        return matchResultRepository.findRanked(MatcherType.EMBEDDING, organisationId,
                f.grade(), f.source(), f.sector(), f.effectiveIncludeClosed(),
                f.tracked() == TrackingFilter.SAVED, f.tracked() == TrackingFilter.SUBMITTED,
                f.closingSoon(), TenderMapper.urgentFrom(), TenderMapper.urgentUntil(),
                LocalDateTime.now(), pageable);
    }

    /**
     * Reads status and blocking-gap count via a projection. Touching the gaps
     * collection here would trigger lazy loading outside the transaction.
     */
    private Map<Long, EligibilityVerdictView> verdictsFor(Organisation organisation, List<Long> tenderIds) {
        if (tenderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, EligibilityVerdictView> out = new HashMap<>();
        for (Object[] row : eligibilityRepository.findSummariesByTenderIds(tenderIds, organisation.getId())) {
            out.put(((Number) row[0]).longValue(),
                    new EligibilityVerdictView((EligibilityStatus) row[1], ((Number) row[2]).intValue()));
        }
        return out;
    }
}
