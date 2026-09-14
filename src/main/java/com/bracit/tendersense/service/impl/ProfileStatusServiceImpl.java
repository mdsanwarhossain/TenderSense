package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ProfileStaleness;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.ProfileStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drives the re-score banner (Profile page) and the dashboard's profile note. Saving a
 * profile is cheap and instant while re-scoring is a pipeline job that takes a lock, so
 * the product has to be able to say plainly that one has outrun the other.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileStatusServiceImpl implements ProfileStatusService {

    private final CapabilityProfileRepository profileRepository;
    private final MatchResultRepository matchResultRepository;
    private final TenderRepository tenderRepository;

    @Override
    public ProfileStaleness staleness(Organisation organisation) {
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
}
