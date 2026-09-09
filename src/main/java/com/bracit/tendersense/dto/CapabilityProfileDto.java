package com.bracit.tendersense.dto;

import java.math.BigDecimal;
import java.util.List;

public record CapabilityProfileDto(
        Long id,
        String orgName,
        String summary,
        BigDecimal annualTurnoverBdt,
        List<String> services,
        List<String> geographies,
        List<ProjectDto> pastProjects,
        List<CertificationDto> certifications) {

    public record ProjectDto(Long id, String title, String client, String description,
                             String sector, BigDecimal valueBdt, Integer year) {
    }

    public record CertificationDto(Long id, String code, String name, String validUntil) {
    }
}
