package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.Sector;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A subscribing company.
 *
 * <p>Tenders are collected and classified once for everyone; scoring, grading and
 * eligibility are per organisation, because they depend on the company's own profile,
 * turnover and certifications.
 *
 * <p>{@link #sectors} is the hard gate: an organisation is only scored against tenders
 * carrying one of its sectors. Ingestion still stores and classifies everything, so a
 * mis-tagged tender is invisible rather than lost — correcting the tag surfaces it with
 * no re-scrape.
 */
@Entity
@Table(name = "organisation",
        uniqueConstraints = @UniqueConstraint(name = "uk_org_slug", columnNames = "slug"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String name;

    /** Stable handle used in the UI and in seed data. */
    @Column(nullable = false, length = 64)
    private String slug;

    @Column(length = 512)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "organisation_sector",
            joinColumns = @JoinColumn(name = "organisation_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sector", length = 32)
    @Builder.Default
    private List<Sector> sectors = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** True for tenants that exist only to demonstrate the product. */
    @Column(name = "demonstration", nullable = false)
    @Builder.Default
    private boolean demonstration = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
