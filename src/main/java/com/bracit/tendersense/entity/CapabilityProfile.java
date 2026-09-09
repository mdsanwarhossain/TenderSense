package com.bracit.tendersense.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** What every tender is matched against. Editing it triggers a full re-score. */
@Entity
@Table(name = "capability_profile",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_profile_org", columnNames = "organisation_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CapabilityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One profile per organisation — the profile IS the tenant's configuration. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    @Column(name = "org_name", nullable = false, length = 256)
    private String orgName;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "annual_turnover_bdt", precision = 18, scale = 2)
    private BigDecimal annualTurnoverBdt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_service", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "service_name", length = 512)
    @Builder.Default
    private List<String> services = new ArrayList<>();

    /**
     * What BracIT does NOT do. A profile that only asserts the positive has nothing
     * to push back with, which is why staffing contracts once ranked first.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_exclusion", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "exclusion", length = 512)
    @Builder.Default
    private List<String> exclusions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_geography", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "geography", length = 128)
    @Builder.Default
    private List<String> geographies = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<PastProject> pastProjects = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<Certification> certifications = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
