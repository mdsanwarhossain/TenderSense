package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.EligibilityStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rules-based, never AI. Eligibility is a yes/no compliance question, so it is
 * decided by fixed rules that are transparent and reproducible.
 */
@Entity
@Table(name = "eligibility_verdict")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EligibilityVerdict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tender_id", unique = true)
    private Tender tender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EligibilityStatus status;

    @OneToMany(mappedBy = "verdict", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EligibilityGap> gaps = new ArrayList<>();

    /** Ordered list of rule codes evaluated, for the audit trail. */
    @Column(name = "rules_applied", length = 512)
    private String rulesApplied;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
