package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.MatcherType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One tender scored by one matcher. Both EMBEDDING and KEYWORD results are stored
 * for every tender, which is what lets the benchmark screen compare persisted
 * scores instead of re-running either system live on stage.
 */
@Entity
@Table(
        name = "match_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_tender_org_matcher",
                columnNames = {"tender_id", "organisation_id", "matcher_type"}),
        indexes = {
                @Index(name = "idx_match_score", columnList = "score"),
                @Index(name = "idx_match_org", columnList = "organisation_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tender_id")
    private Tender tender;

    /** Whose score this is. The same tender scores differently for different companies. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(name = "matcher_type", nullable = false, length = 32)
    private MatcherType matcherType;

    /** Normalised 0..1. Cosine similarity for EMBEDDING, normalised BM25 for KEYWORD. */
    @Column(nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(length = 4)
    private MatchGrade grade;

    /** Top contributing profile/tender chunk pairs, as JSON. Drives the evidence panel. */
    @Column(name = "evidence_json", columnDefinition = "text")
    private String evidenceJson;

    /** Plain-language "why this matched" text. Never used for a pass/fail decision. */
    @Column(name = "summary_text", columnDefinition = "text")
    private String summaryText;

    /** The excluded-work statement this tender resembled, if the penalty fired. */
    @Column(name = "exclusion_text", length = 512)
    private String exclusionText;

    @Column(name = "exclusion_penalty")
    private Double exclusionPenalty;

    /** Embedding model identity; a change here invalidates every stored score. */
    @Column(name = "model_version", length = 128)
    private String modelVersion;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "duration_ms")
    private Long durationMs;
}
