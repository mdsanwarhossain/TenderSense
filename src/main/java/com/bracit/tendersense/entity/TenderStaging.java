package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.StagingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A fetched tender waiting to be processed: the first stop for everything the scrapers
 * and APIs bring in.
 *
 * <p>The worker takes a few rows at a time, has the local model read them, and writes the
 * result into {@link Tender}. Keeping the row afterwards gives a history of every
 * version fetched, and lets a tender be processed again without fetching it again.
 */
@Entity
@Table(
        name = "tender_staging",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_staging_version",
                columnNames = {"source_portal", "external_id", "content_hash", "parser_version"}),
        indexes = @Index(name = "idx_staging_status_closing", columnList = "status, closing_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TenderStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_portal", nullable = false, length = 32)
    private SourcePortal sourcePortal;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "parser_version", length = 32)
    private String parserVersion;

    /** Copied out of the payload so the worker can take the soonest deadline first. */
    @Column(name = "closing_at")
    private LocalDateTime closingAt;

    /** The tender as the rules parsed it, before the model reads it. */
    @Column(name = "parsed_json", nullable = false, columnDefinition = "text")
    private String parsedJson;

    /** The payload as fetched, for sources with no snapshot file (JSON, listing rows). */
    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private StagingStatus status = StagingStatus.PENDING;

    @Builder.Default
    private int attempts = 0;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    /** The tender row this became. */
    @Column(name = "tender_id")
    private Long tenderId;

    /** Whether the model was called for this row (closed tenders skip it). */
    @Column(name = "ai_used")
    private Boolean aiUsed;

    @Column(name = "ai_duration_ms")
    private Long aiDurationMs;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
