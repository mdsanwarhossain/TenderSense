package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.BidAction;
import com.bracit.tendersense.entity.enums.EligibilityStatus;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.time.Instant;
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
        String whyMatched,
        // What this company's team has done about it -- see TenderTracking.
        boolean wishlisted,
        boolean submitted,
        Instant submittedAt,
        /** The tender's own page on its portal; null when it cannot be built. */
        String sourceUrl,
        /** The local model's shorter title, when the portal's is too long to scan; else null. */
        String shortTitle) {
}
