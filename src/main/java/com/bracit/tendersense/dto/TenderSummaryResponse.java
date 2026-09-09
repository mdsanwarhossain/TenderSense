package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.BidAction;
import com.bracit.tendersense.entity.enums.EligibilityStatus;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.time.LocalDateTime;

/** One row of the morning shortlist -- the primary demo screen. */
public record TenderSummaryResponse(
        Long id,
        String externalId,
        SourcePortal source,
        String title,
        String procuringEntity,
        String procurementNature,
        String procurementMethod,
        LocalDateTime closingAt,
        Integer daysToDeadline,
        boolean urgent,
        MatchGrade grade,
        Double score,
        EligibilityStatus eligibility,
        int blockingGapCount,
        BidAction recommendation,
        String whyMatched) {
}
