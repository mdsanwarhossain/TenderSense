package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A tender as captured from a source portal.
 *
 * <p>Field coverage differs by source and that is expected: e-GP supplies the rich
 * procuring-entity chain and {@code eligibilityText}, World Bank supplies project
 * identifiers. Absent fields stay null rather than being defaulted, so the
 * eligibility rules can distinguish "requirement not met" from "not stated".
 */
@Entity
@Table(
        name = "tender",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tender_source_external",
                columnNames = {"source_portal", "external_id"}),
        indexes = {
                @Index(name = "idx_tender_closing", columnList = "closing_at"),
                @Index(name = "idx_tender_published", columnList = "published_at"),
                @Index(name = "idx_tender_sector", columnList = "sector")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_portal", nullable = false, length = 32)
    private SourcePortal sourcePortal;

    /** Portal-native identifier: e-GP tenderId, or World Bank notice id. */
    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "reference_no", length = 512)
    private String referenceNo;

    @Column(length = 2000)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "procurement_nature", length = 128)
    private String procurementNature;

    @Column(name = "procurement_type", length = 64)
    private String procurementType;

    @Column(name = "procurement_method", length = 256)
    private String procurementMethod;

    @Column(length = 512)
    private String ministry;

    @Column(length = 512)
    private String division;

    @Column(length = 512)
    private String organization;

    @Column(name = "procuring_entity", length = 512)
    private String procuringEntity;

    @Column(name = "pe_code", length = 64)
    private String peCode;

    @Column(length = 128)
    private String district;

    @Column(length = 128)
    private String country;

    @Column(name = "budget_type", length = 128)
    private String budgetType;

    @Column(name = "source_of_funds", length = 256)
    private String sourceOfFunds;

    @Column(name = "document_price_bdt", precision = 18, scale = 2)
    private BigDecimal documentPriceBdt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "closing_at")
    private LocalDateTime closingAt;

    @Column(length = 64)
    private String status;

    /**
     * The portal's own category string, semicolon-delimited broad-to-narrow. e-GP
     * supplies CPV on every tender observed; World Bank supplies none.
     */
    @Column(name = "cpv_raw", columnDefinition = "text")
    private String cpvRaw;

    /** The CPV division — the first segment, which is what decides the sector. */
    @Column(name = "cpv_top", length = 256)
    private String cpvTop;

    /**
     * Shared across every organisation: classification is a property of the tender,
     * not of who is looking at it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sector", length = 32)
    private Sector sector;

    /**
     * Raw "Eligibility of Tenderer" text from e-GP. This is the rules engine's
     * primary input -- the reason eligibility does not need PDF extraction.
     */
    @Column(name = "eligibility_text", columnDefinition = "text")
    private String eligibilityText;

    /**
     * The tender's embedding — computed ONCE and reused by every organisation.
     *
     * <p>Embedding is ~99% of scoring cost (~70 ms/tender); cosine against a profile is
     * microseconds. Storing the vector is what makes per-company scoring nearly free and
     * turns a full rescore from minutes into seconds.
     *
     * <p>Stored as {@code real[]} rather than a pgvector column because scoring is an
     * exact in-memory scan, which needs no index. A pgvector column plus ANN is the
     * upgrade path if the organisation count ever outgrows a handful.
     */
    @Column(name = "embedding", columnDefinition = "real[]")
    private float[] embedding;

    /** Model that produced {@link #embedding}; a change here invalidates the vector. */
    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    /** Path to the raw HTML/JSON snapshot this record was parsed from (provenance). */
    @Column(name = "raw_snapshot_path", length = 512)
    private String rawSnapshotPath;

    /** Hash of the source payload; a change means a corrigendum was published. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Parser that produced this row. contentHash covers the *source* HTML, so a
     * parser fix alone would otherwise look like "unchanged" and never re-parse
     * already-stored tenders. Ingestion treats a version bump as a revision.
     */
    @Column(name = "parser_version", length = 32)
    private String parserVersion;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revision_count", nullable = false)
    @Builder.Default
    private int revisionCount = 0;
}
