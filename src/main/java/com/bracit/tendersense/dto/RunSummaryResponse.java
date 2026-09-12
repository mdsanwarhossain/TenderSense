package com.bracit.tendersense.dto;

import java.time.Instant;

/**
 * Totals over every collection run, for the Pipeline page's cards. Computed in the
 * database, since the run table itself is now read a page at a time.
 */
public record RunSummaryResponse(long runsTotal,
                                 long failedTotal,
                                 long failedLast24h,
                                 Instant lastSuccessAt,
                                 long tendersScored) {
}
