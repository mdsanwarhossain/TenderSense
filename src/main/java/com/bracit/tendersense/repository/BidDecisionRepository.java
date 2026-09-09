package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.BidDecision;
import com.bracit.tendersense.entity.enums.BidAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidDecisionRepository extends JpaRepository<BidDecision, Long> {

    Optional<BidDecision> findFirstByTenderIdAndOrganisationIdOrderByDecidedAtDesc(
            Long tenderId, Long organisationId);

    /** Negative examples for the feedback loop. */
    List<BidDecision> findByOrganisationIdAndAction(Long organisationId, BidAction action);
}
