package com.bracit.tendersense.util;

import com.bracit.tendersense.dto.TenderDetailResponse;
import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.enums.LlmReviewStatus;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.BidAction;
import com.bracit.tendersense.entity.enums.EligibilityStatus;
import com.bracit.tendersense.entity.enums.EligibilityVerdictView;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class TenderMapper {

    /** A tender closing within this window is flagged urgent on the shortlist. */
    public static final int URGENT_DAYS = 7;

    public TenderSummaryResponse toSummary(Tender t, MatchResult match, EligibilityVerdictView elig) {
        Integer days = daysToDeadline(t.getClosingAt());
        return new TenderSummaryResponse(
                t.getId(),
                t.getExternalId(),
                t.getSourcePortal(),
                t.getTitle(),
                t.getProcuringEntity(),
                t.getProcurementNature(),
                t.getProcurementMethod(),
                t.getClosingAt(),
                days,
                days != null && days <= URGENT_DAYS && days >= 0,
                match == null ? null : match.getGrade(),
                match == null ? null : match.getScore(),
                elig == null ? null : elig.status(),
                elig == null ? 0 : elig.blockingGapCount(),
                recommend(match, elig),
                match == null ? null : match.getSummaryText(),
                scoredOnly(match, match == null ? null : match.getLlmScore()),
                scoredOnly(match, match == null ? null : match.getLlmReasoning()),
                match == null ? null : match.getLlmStatus());
    }

    /**
     * A verdict is shown only while it is current. A STALE or FAILED row still holds its
     * old values in the database, for comparison, but must not read as today's answer.
     */
    private static <T> T scoredOnly(MatchResult match, T value) {
        return match != null && match.getLlmStatus() == LlmReviewStatus.SCORED ? value : null;
    }

    public TenderDetailResponse toDetail(Tender t) {
        return new TenderDetailResponse(
                t.getId(), t.getExternalId(), t.getSourcePortal(), t.getReferenceNo(),
                t.getTitle(), t.getDescription(), t.getMinistry(), t.getDivision(),
                t.getOrganization(), t.getProcuringEntity(), t.getDistrict(), t.getCountry(),
                t.getProcurementNature(), t.getProcurementType(), t.getProcurementMethod(),
                t.getBudgetType(), t.getSourceOfFunds(), t.getDocumentPriceBdt(),
                t.getPublishedAt(), t.getClosingAt(), daysToDeadline(t.getClosingAt()),
                t.getStatus(), t.getEligibilityText(), t.getRawSnapshotPath(),
                t.getContentHash(), t.getRevisionCount());
    }

    public static Integer daysToDeadline(LocalDateTime closingAt) {
        if (closingAt == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), closingAt.toLocalDate());
    }

    /**
     * Bid / hold / skip. A blocking eligibility failure always wins over a strong
     * match score: there is no point recommending a tender we cannot legally enter.
     */
    public BidAction recommend(MatchResult match, EligibilityVerdictView elig) {
        if (elig != null && elig.status() == EligibilityStatus.INELIGIBLE) {
            return BidAction.SKIP;
        }
        if (match == null || match.getGrade() == null) {
            return null;
        }
        return switch (match.getGrade()) {
            case S, A -> elig != null && elig.status() == EligibilityStatus.NEEDS_VERIFICATION
                    ? BidAction.HOLD : BidAction.BID;
            case B -> BidAction.HOLD;
            case C -> BidAction.SKIP;
        };
    }
}
