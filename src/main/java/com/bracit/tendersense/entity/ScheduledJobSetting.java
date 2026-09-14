package com.bracit.tendersense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

/**
 * One scheduled job's schedule, as the admin set it. Created from the
 * {@code tendersense.schedule.*} defaults the first time the application starts on a
 * database; after that this row is what runs.
 */
@Entity
@Table(name = "schedule_job")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduledJobSetting {

    /** ScheduledJob.key(), e.g. "bracSync". */
    @Id
    @Column(name = "job_key", length = 40)
    private String jobKey;

    /** Spring cron, six fields, in the schedule zone (Asia/Dhaka). */
    @Column(nullable = false, length = 120)
    private String cron;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** The admin who last changed it; null while it still has its first default. */
    @Column(name = "updated_by", length = 254)
    private String updatedBy;

    /**
     * When the scheduler last fired it -- recorded before the run, so a run skipped for the
     * pipeline lock or paused after failures still counts as a firing.
     */
    @Column(name = "last_fired_at")
    private Instant lastFiredAt;
}
