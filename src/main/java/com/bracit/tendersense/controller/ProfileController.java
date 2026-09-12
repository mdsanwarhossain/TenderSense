package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.CapabilityProfileDto;
import com.bracit.tendersense.dto.ProfileStaleness;
import com.bracit.tendersense.dto.ProfileUpdateRequest;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.ProfileStatusService;
import com.bracit.tendersense.util.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** The signed-in company's capability profile: what every tender is matched against. */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CapabilityProfileRepository profileRepository;
    private final CapabilityProfileService profileService;
    private final ProfileStatusService profileStatusService;

    @GetMapping
    public CapabilityProfileDto profile(@CurrentOrganisation Organisation organisation) {
        return profileRepository.findByOrganisationId(organisation.getId())
                .map(p -> ProfileMapper.toDto(p, organisation)).orElse(null);
    }

    @PutMapping
    public CapabilityProfileDto update(@CurrentOrganisation Organisation organisation,
                                       @RequestBody ProfileUpdateRequest request) {
        CapabilityProfile saved = profileService.update(organisation, request);
        return ProfileMapper.toDto(saved, organisation);
    }

    /** Whether the stored scores still reflect the stored profile; drives the re-score banner. */
    @GetMapping("/staleness")
    public ProfileStaleness staleness(@CurrentOrganisation Organisation organisation) {
        return profileStatusService.staleness(organisation);
    }
}
