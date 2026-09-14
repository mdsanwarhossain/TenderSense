package com.bracit.tendersense.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the detail page shows in place of "Matched Criteria", and whether to keep asking.
 *
 * @param status      READY: use it. GENERATING: the model is writing, poll again.
 *                    UNAVAILABLE: no comparison and none coming -- show what else there is.
 * @param comparison  two sentences on the tender against this company's work
 * @param matches     lines naming both sides; empty while generating
 * @param gaps        what the tender needs that the profile does not show
 * @param writtenBy   the model that wrote it, or null when this is the stored sentence
 * @param generatedAt when it was written, for the "written N ago" line
 */
public record MatchSummaryResponse(
        Status status,
        String comparison,
        List<String> matches,
        List<String> gaps,
        String writtenBy,
        Instant generatedAt) {

    public enum Status { READY, GENERATING, UNAVAILABLE }

    public static MatchSummaryResponse generating() {
        return new MatchSummaryResponse(Status.GENERATING, null, List.of(), List.of(), null, null);
    }

    public static MatchSummaryResponse unavailable(String comparison) {
        return new MatchSummaryResponse(Status.UNAVAILABLE, comparison, List.of(), List.of(), null, null);
    }
}
