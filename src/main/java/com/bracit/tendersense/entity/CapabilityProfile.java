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
@Table(name = "capability_profile")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CapabilityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
