package com.bracit.tendersense.controller;

import com.bracit.tendersense.dto.CapabilityProfileDto;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** BracIT's capability profile: what every tender is matched against. */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CapabilityProfileRepository profileRepository;

    @GetMapping
    public CapabilityProfileDto profile() {
        return profileRepository.findFirstByOrderByIdAsc().map(this::toDto).orElse(null);
    }

    private CapabilityProfileDto toDto(CapabilityProfile p) {
        return new CapabilityProfileDto(
                p.getId(), p.getOrgName(), p.getSummary(), p.getAnnualTurnoverBdt(),
                p.getServices(), p.getGeographies(),
                p.getPastProjects().stream()
                        .map(x -> new CapabilityProfileDto.ProjectDto(
                                x.getId(), x.getTitle(), x.getClient(), x.getDescription(),
                                x.getSector(), x.getValueBdt(), x.getYear()))
                        .toList(),
                p.getCertifications().stream()
                        .map(c -> new CapabilityProfileDto.CertificationDto(
                                c.getId(), c.getCode(), c.getName(),
                                Objects.toString(c.getValidUntil(), null)))
                        .toList());
    }
}
