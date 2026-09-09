package com.bracit.tendersense.dto;

import java.time.Instant;

/**
 * Whether the stored scores still reflect the stored profile.
 *
 * @param stale       true when the profile was edited after the newest score was computed
 * @param scoredCount how many tenders currently hold a score for this company
 * @param scorable    how many tenders fall inside this company's sectors, i.e. the number
 *                    a re-score would actually cover
 */
public record ProfileStaleness(boolean stale,
                               Instant profileUpdatedAt,
                               Instant lastScoredAt,
                               long scoredCount,
                               long scorable) {}
