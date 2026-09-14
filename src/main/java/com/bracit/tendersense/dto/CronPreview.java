package com.bracit.tendersense.dto;

import java.time.Instant;
import java.util.List;

/**
 * What a schedule would do, before it is saved: the next few runs, or why it is refused.
 *
 * @param cron    the expression as it would be saved (a five-field one gains its seconds)
 * @param message why it is refused; null when valid
 */
public record CronPreview(String cron, boolean valid, String message, List<Instant> nextRuns) {
}
