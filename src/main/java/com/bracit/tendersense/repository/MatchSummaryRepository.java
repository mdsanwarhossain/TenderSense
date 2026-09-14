package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.MatchSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchSummaryRepository extends JpaRepository<MatchSummary, Long> {

    Optional<MatchSummary> findByTenderIdAndOrganisationId(Long tenderId, Long organisationId);
}
