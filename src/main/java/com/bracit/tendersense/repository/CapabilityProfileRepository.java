package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.CapabilityProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CapabilityProfileRepository extends JpaRepository<CapabilityProfile, Long> {

    Optional<CapabilityProfile> findByOrganisationId(Long organisationId);

    Optional<CapabilityProfile> findByOrganisationSlug(String slug);
}
