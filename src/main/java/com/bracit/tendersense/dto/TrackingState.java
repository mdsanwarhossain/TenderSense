package com.bracit.tendersense.dto;

import java.time.Instant;

/** A company's saved / submitted state for one tender. Also the tracking endpoints' reply. */
public record TrackingState(Long tenderId,
                            boolean wishlisted,
                            Instant wishlistedAt,
                            boolean submitted,
                            Instant submittedAt) {

    public static TrackingState of(Long tenderId, Instant wishlistedAt, Instant submittedAt) {
        return new TrackingState(tenderId, wishlistedAt != null, wishlistedAt,
                submittedAt != null, submittedAt);
    }

    public static TrackingState none(Long tenderId) {
        return of(tenderId, null, null);
    }
}
