package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.TenderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenderTrackingRepository extends JpaRepository<TenderTracking, Long> {

    Optional<TenderTracking> findByTenderIdAndOrganisationId(Long tenderId, Long organisationId);

    /**
     * One query for a whole shortlist page, instead of one lookup per row:
     * {@code [tenderId, wishlistedAt, submittedAt]}.
     */
    @Query("""
           select t.tender.id, t.wishlistedAt, t.submittedAt from TenderTracking t
           where t.organisation.id = :organisationId and t.tender.id in :tenderIds
           """)
    List<Object[]> findStates(@Param("organisationId") Long organisationId,
                              @Param("tenderIds") Collection<Long> tenderIds);
}
