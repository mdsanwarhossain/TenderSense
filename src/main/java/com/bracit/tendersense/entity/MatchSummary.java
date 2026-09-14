package com.bracit.tendersense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * The local model's written comparison of one tender with one company's profile.
 *
 * <p>Written on demand -- the first time someone opens the tender -- because a comparison
 * costs the model real seconds and most tenders are never opened. Kept for a day
 * ({@code tendersense.llm.summary-cache-hours}) so the second reader waits for nothing.
 *
 * <p>{@code inputHash} is over the exact prompt the model was given. A re-read tender or
 * an edited profile changes the prompt, so the stale comparison is rewritten even inside
 * the day; {@code promptVersion} and {@code model} do the same when we change either.
 */
@Entity
@Table(
        name = "match_summary",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_summary_tender_org",
                columnNames = {"tender_id", "organisation_id"}),
        indexes = @Index(name = "idx_match_summary_org", columnList = "organisation_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tender_id", nullable = false)
    private Tender tender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** Two sentences: what the tender needs, and how far this company's work covers it. */
    @Column(columnDefinition = "text")
    private String comparison;

    /** Each line names both sides, "what the tender asks — the work that covers it". */
    @Column(columnDefinition = "text[]")
    private String[] matches;

    @Column(columnDefinition = "text[]")
    private String[] gaps;

    @Column(length = 64)
    private String model;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    /** SHA-256 of the prompt, so a changed tender or profile rewrites the comparison. */
    @Column(name = "input_hash", length = 64)
    private String inputHash;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "duration_ms")
    private Long durationMs;
}
