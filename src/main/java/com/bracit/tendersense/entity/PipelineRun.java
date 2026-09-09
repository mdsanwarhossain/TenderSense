package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.RunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Telemetry + provenance: what ran, when, how long, and what it produced. */
@Entity
@Table(name = "pipeline_run", indexes = @Index(name = "idx_run_started", columnList = "started_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "tenders_discovered")
    private Integer tendersDiscovered;

    @Column(name = "tenders_detailed")
    private Integer tendersDetailed;

    @Column(name = "tenders_scored")
    private Integer tendersScored;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;
}
