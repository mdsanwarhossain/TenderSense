package com.bracit.tendersense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "certification")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private CapabilityProfile profile;

    /** Normalised code the rules engine matches on, e.g. ISO27001, ISO9001, CMMI3. */
    @Column(nullable = false, length = 64)
    private String code;

    @Column(length = 256)
    private String name;

    @Column(name = "valid_until")
    private LocalDate validUntil;
}
