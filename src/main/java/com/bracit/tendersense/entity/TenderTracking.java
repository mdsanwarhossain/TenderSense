package com.bracit.tendersense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * What a company's tender team has done about one tender: saved it for later, and/or
 * marked that they submitted a bid on the portal.
 *
 * <p>Current state, one row per tender per company -- not a log. Deliberately separate
 * from {@link BidDecision}, which is an append-only record of BID / HOLD / SKIP calls
 * with notes; this is a pair of toggles read for every shortlist row on every load.
 *
 * <p>A timestamp is both the flag and the "since when": null means off.
 */
@Entity
@Table(name = "tender_tracking",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tracking_tender_org", columnNames = {"tender_id", "organisation_id"}),
        indexes = @Index(name = "idx_tracking_org", columnList = "organisation_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TenderTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tender_id")
    private Tender tender;

    /** Tracking is per company: BracIT saving a tender says nothing about Padma. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    /** When the team saved it for later. Null = not saved. */
    @Column(name = "wishlisted_at")
    private Instant wishlistedAt;

    /** When the team marked it as submitted on the portal. Null = not submitted. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
