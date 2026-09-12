package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.DashboardResponse;
import com.bracit.tendersense.dto.DashboardResponse.TrackingChange;
import com.bracit.tendersense.dto.ShortlistFilter;
import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.TenderTracking;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.TrackingFilter;
import com.bracit.tendersense.repository.TenderTrackingRepository;
import com.bracit.tendersense.service.DashboardService;
import com.bracit.tendersense.service.NotificationService;
import com.bracit.tendersense.service.ProfileStatusService;
import com.bracit.tendersense.service.ShortlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    static final int SHOWN = 5;
    /** How far down the closing-soon ranking to look for tenders worth acting on. */
    static final int CLOSING_SOON_SCAN = 50;

    private final ShortlistService shortlistService;
    private final NotificationService notificationService;
    private final ProfileStatusService profileStatusService;
    private final TenderTrackingRepository trackingRepository;

    @Override
    public DashboardResponse dashboard(Organisation organisation) {
        ShortlistFilter open = ShortlistFilter.OPEN;
        var numbers = new DashboardResponse.Numbers(
                shortlistService.count(organisation, open),
                shortlistService.count(organisation, open.withGrade(MatchGrade.S)),
                shortlistService.count(organisation, open.withGrade(MatchGrade.A)),
                shortlistService.count(organisation, open.withClosingSoon(true)),
                shortlistService.count(organisation, open.withTracked(TrackingFilter.SAVED)),
                shortlistService.count(organisation, open.withTracked(TrackingFilter.SUBMITTED)));

        List<TenderSummaryResponse> best =
                shortlistService.list(organisation, open, PageRequest.of(0, SHOWN)).content();

        List<TenderSummaryResponse> closingSoon = worthActingOn(shortlistService.list(organisation,
                open.withClosingSoon(true), PageRequest.of(0, CLOSING_SOON_SCAN)).content());

        var activity = new DashboardResponse.Activity(
                notificationService.unreadCount(organisation),
                notificationService.list(organisation, false, PageRequest.of(0, SHOWN)).content(),
                trackingRepository.findRecent(organisation.getId(), PageRequest.of(0, SHOWN)).stream()
                        .map(DashboardServiceImpl::toChange)
                        .toList(),
                profileStatusService.staleness(organisation));

        return new DashboardResponse(numbers, best, closingSoon, activity);
    }

    /**
     * Of the tenders closing within the week, the ones a team would act on -- saved, or
     * graded S or A -- soonest deadline first. A C-grade tender closing tomorrow is noise.
     */
    static List<TenderSummaryResponse> worthActingOn(List<TenderSummaryResponse> closingSoon) {
        return closingSoon.stream()
                .filter(t -> t.wishlisted() || t.grade() == MatchGrade.S || t.grade() == MatchGrade.A)
                .sorted(Comparator.comparing(TenderSummaryResponse::closingAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(SHOWN)
                .toList();
    }

    private static TrackingChange toChange(TenderTracking t) {
        Tender tender = t.getTender();
        String title = tender.getAiShortTitle() != null ? tender.getAiShortTitle() : tender.getTitle();
        return new TrackingChange(tender.getId(), title, tender.getSourcePortal(),
                t.getWishlistedAt() != null, t.getSubmittedAt() != null, t.getUpdatedAt());
    }
}
