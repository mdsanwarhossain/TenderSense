package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.BidAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * The BD team's call on a tender. Feeds the feedback loop: SKIP decisions become
 * negative examples that adjust ranking.
 */
@Entity
@Table(name = "bid_decision")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BidDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tender_id")
    private Tender tender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BidAction action;

    @Column(length = 1024)
    private String note;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
