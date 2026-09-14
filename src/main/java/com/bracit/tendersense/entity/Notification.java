package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.MatchGrade;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * One surfaced match: "this tender is an S/A-grade fit for your profile."
 *
 * <p>Tender fields are snapshotted at creation time rather than read through the
 * {@link #tender} association. {@code open-in-view} is off, so a lazy load past the
 * creating transaction throws; the list screen and the bell dropdown both render
 * well after that transaction has closed, so the fields they need are copied here
 * the same way {@code MatchResult.summaryText} is computed once and stored rather
 * than derived on read.
 */
@Entity
@Table(
        name = "notification",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_org_tender", columnNames = {"organisation_id", "tender_id"}),
        indexes = {
                @Index(name = "idx_notification_org_created", columnList = "organisation_id, created_at"),
                @Index(name = "idx_notification_org_unread", columnList = "organisation_id, read_flag")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Whose bell this rings. A match is only ever notified to the company it matched. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tender_id")
    private Tender tender;

    @Column(name = "tender_title", length = 2000)
    private String tenderTitle;

    @Column(name = "procuring_entity", length = 512)
    private String procuringEntity;

    @Column(name = "closing_at")
    private LocalDateTime closingAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 4, nullable = false)
    private MatchGrade grade;

    @Column(nullable = false)
    private double score;

    @Column(name = "read_flag", nullable = false)
    @Builder.Default
    private boolean readFlag = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
