package com.bracit.tendersense.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Not just "failed" but why and by how much -- the BRD asks for "what BracIT still
 * needs to bid", so a gap carries the requirement, the actual value, and whether
 * it blocks the bid outright.
 */
@Entity
@Table(name = "eligibility_gap")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EligibilityGap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verdict_id")
    private EligibilityVerdict verdict;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(length = 512)
    private String requirement;

    @Column(length = 512)
    private String actual;

    /** False when the rule could not be evaluated from portal data (verify manually). */
    @Column(nullable = false)
    private boolean blocking;

    @Column(length = 1024)
    private String message;
}
