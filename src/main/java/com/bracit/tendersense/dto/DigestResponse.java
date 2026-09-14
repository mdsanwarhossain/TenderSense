package com.bracit.tendersense.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * What the morning digest reports: the day's shortlist plus the counts a bid
 * manager scans before opening it.
 */
public record DigestResponse(
        LocalDate date,
        long openTenders,
        int sGrade,
        int aGrade,
        int closingWithinSevenDays,
        int needingVerification,
        List<TenderSummaryResponse> top) {
}
