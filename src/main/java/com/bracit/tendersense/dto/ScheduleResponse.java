package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.RunStatus;

import java.time.Instant;
import java.util.List;

/**
 * The scheduled jobs as they are set now, with when each next fires and how its last run
 * went.
 *
 * @param enabled false when scheduling is off on this server
 *                ({@code tendersense.schedule.enabled=false}): nothing fires
 */
public record ScheduleResponse(boolean enabled, String zone, List<Job> jobs) {

    /**
     * @param cron          the Spring cron in force, in {@code zone}
     * @param defaultCron   what "back to default" restores
     * @param enabled       the admin's on/off switch for this job
     * @param paused        sitting out its runs after repeated failures
     * @param nextRunAt     null when the job is off, or scheduling is off on this server
     * @param lastFiredAt   when the scheduler last fired it; for a job not fired since firings
     *                      were recorded, its last recorded run
     * @param lastRunAt     the last recorded run; null for the digest, which records none
     * @param avgDurationMs mean duration over its finished runs
     * @param updatedBy     the admin who last changed it; null while it has its default
     */
    public record Job(String key,
                      String label,
                      String description,
                      String cron,
                      String defaultCron,
                      boolean enabled,
                      boolean paused,
                      Instant nextRunAt,
                      Instant lastFiredAt,
                      Instant lastRunAt,
                      RunStatus lastStatus,
                      Long avgDurationMs,
                      Instant updatedAt,
                      String updatedBy) {
    }
}
