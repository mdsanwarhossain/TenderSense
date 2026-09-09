package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.CapabilityProfileDto;
import com.bracit.tendersense.dto.ProfileStaleness;
import com.bracit.tendersense.dto.ProfileUpdateRequest;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The signed-in company's capability profile: what every tender is matched against. */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CapabilityProfileRepository profileRepository;
    private final CapabilityProfileService profileService;
    private final MatchResultRepository matchResultRepository;
    private final TenderRepository tenderRepository;

    @GetMapping
    public CapabilityProfileDto profile(@CurrentOrganisation Organisation organisation) {
        return profileRepository.findByOrganisationId(organisation.getId())
                .map(p -> toDto(p, organisation)).orElse(null);
    }

    @PutMapping
    public CapabilityProfileDto update(@CurrentOrganisation Organisation organisation,
                                       @RequestBody ProfileUpdateRequest request) {
        CapabilityProfile saved = profileService.update(organisation, request);
        return toDto(saved, organisation);
    }

    /**
     * Whether the stored scores still reflect the stored profile.
     *
     * <p>Drives the re-score banner. Saving is deliberately cheap and instant while
     * re-scoring is a pipeline job that takes a lock, so the product has to be able to
     * say plainly that one has outrun the other.
     */
    @GetMapping("/staleness")
    public ProfileStaleness staleness(@CurrentOrganisation Organisation organisation) {
        Instant updatedAt = profileRepository.findByOrganisationId(organisation.getId())
                .map(CapabilityProfile::getUpdatedAt).orElse(null);
        Instant scoredAt = matchResultRepository.lastComputedAt(organisation.getId());

        List<Sector> sectors = organisation.getSectors();
        long scorable = sectors.isEmpty()
                ? tenderRepository.count()
                : tenderRepository.countInSectors(sectors);

        // No scores yet is stale by definition -- a new company has a profile and nothing
        // computed from it, which is exactly the state the banner should be shouting about.
        boolean stale = scoredAt == null
                || (updatedAt != null && updatedAt.isAfter(scoredAt));

        return new ProfileStaleness(stale, updatedAt, scoredAt,
                matchResultRepository.countByOrganisationIdAndMatcherType(
                        organisation.getId(), MatcherType.EMBEDDING),
                scorable);
    }

    private CapabilityProfileDto toDto(CapabilityProfile p, Organisation organisation) {
        return new CapabilityProfileDto(
                p.getId(), p.getOrgName(), p.getSummary(), p.getAnnualTurnoverBdt(),
                p.getServices(), p.getExclusions(),
                organisation.getSectors().stream().map(Enum::name).toList(),
                p.getGeographies(),
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
