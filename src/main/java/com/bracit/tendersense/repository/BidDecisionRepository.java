package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.BidDecision;
import com.bracit.tendersense.entity.enums.BidAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidDecisionRepository extends JpaRepository<BidDecision, Long> {

    Optional<BidDecision> findFirstByTenderIdOrderByDecidedAtDesc(Long tenderId);

    /** Negative examples for the feedback loop. */
    List<BidDecision> findByAction(BidAction action);
}
