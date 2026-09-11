package com.bracit.tendersense.dto;

import java.time.Instant;

/**
 * The staging queue, for the Pipeline screen.
 *
 * @param waitingOpen     open tenders waiting for the model -- the ones that matter
 * @param readLastHour    tenders the model read in the last hour
 * @param secondsPerRead  average model time per tender, measured
 * @param etaMinutes      waitingOpen x secondsPerRead; null until something has been read
 */
public record ProcessingStatusResponse(
        boolean modelEnabled,
        boolean workerEnabled,
        String model,
        long waiting,
        long waitingOpen,
        long inProgress,
        long awaitingScore,
        long done,
        long failed,
        long readLastHour,
        Double secondsPerRead,
        Long etaMinutes,
        String lastError,
        Instant lastErrorAt) {
}
