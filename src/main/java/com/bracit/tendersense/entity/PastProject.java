package com.bracit.tendersense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "past_project")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PastProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private CapabilityProfile profile;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 256)
    private String client;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 128)
    private String sector;

    @Column(name = "value_bdt", precision = 18, scale = 2)
    private BigDecimal valueBdt;

    private Integer year;
}
