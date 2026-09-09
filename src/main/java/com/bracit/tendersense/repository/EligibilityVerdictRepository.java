package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.enums.EligibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EligibilityVerdictRepository extends JpaRepository<EligibilityVerdict, Long> {

    Optional<EligibilityVerdict> findByTenderIdAndOrganisationId(Long tenderId, Long organisationId);

    /**
     * Loads the gaps alongside the verdict. Without the fetch join the detail endpoint
     * blows up with LazyInitializationException, because open-in-view is disabled and
     * the controller reads the collection after the transaction has closed.
     */
    @Query("""
           select v from EligibilityVerdict v
           left join fetch v.gaps
           where v.tender.id = :tenderId and v.organisation.id = :organisationId
           """)
    Optional<EligibilityVerdict> findByTenderIdWithGaps(@Param("tenderId") Long tenderId,
                                                        @Param("organisationId") Long organisationId);

    /**
     * Status plus blocking-gap count for a whole page, in one query. The shortlist needs
     * only these two facts, so loading full verdict graphs per row would be both an N+1
     * and a lazy-loading hazard.
     */
    @Query("""
           select v.tender.id, v.status,
                  (select count(g) from EligibilityGap g where g.verdict = v and g.blocking = true)
           from EligibilityVerdict v
           where v.tender.id in :tenderIds and v.organisation.id = :organisationId
           """)
    List<Object[]> findSummariesByTenderIds(@Param("tenderIds") Collection<Long> tenderIds,
                                            @Param("organisationId") Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, EligibilityStatus status);

    void deleteByOrganisationId(Long organisationId);
}
