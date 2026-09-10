package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.LlmReviewStatus;
import com.bracit.tendersense.entity.enums.MatcherType;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
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
/*
 * @DynamicUpdate: ScoringServiceImpl.persist() loads this row and saves the whole entity.
 * Without it, a rescore overlapping the background LLM review would write back the llm_*
 * values it read -- stale nulls -- over a verdict committed a moment earlier. With it,
 * Hibernate writes only the columns persist() actually changed, which never include llm_*.
 */
@DynamicUpdate
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

    // ---- second-stage LLM review. Written only by LlmReviewService, via targeted
    // ---- updates; only ever populated on the EMBEDDING row.

    /** The model's 0-100 verdict. Shown beside {@link #score}; never used for ranking. */
    @Column(name = "llm_score")
    private Integer llmScore;

    @Column(name = "llm_reasoning", columnDefinition = "text")
    private String llmReasoning;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_status", length = 16)
    private LlmReviewStatus llmStatus;

    @Column(name = "llm_model", length = 64)
    private String llmModel;

    /** Fingerprint of tender, profile version, model and prompt; decides staleness. */
    @Column(name = "llm_input_hash", length = 64)
    private String llmInputHash;

    @Column(name = "llm_error", length = 512)
    private String llmError;

    @Column(name = "llm_scored_at")
    private Instant llmScoredAt;

    @Column(name = "llm_duration_ms")
    private Long llmDurationMs;
}
