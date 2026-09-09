package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TenderRepository extends JpaRepository<Tender, Long> {

    Optional<Tender> findBySourcePortalAndExternalId(SourcePortal portal, String externalId);

    boolean existsBySourcePortalAndExternalId(SourcePortal portal, String externalId);

    /**
     * Bulk membership check used by the discovery stop-rule: pulling every known id
     * one-by-one would issue thousands of queries per sweep.
     */
    @Query("select t.externalId from Tender t where t.sourcePortal = :portal and t.externalId in :ids")
    Set<String> findKnownExternalIds(@Param("portal") SourcePortal portal,
                                     @Param("ids") Collection<String> ids);

    @Query("select t.externalId from Tender t where t.sourcePortal = :portal")
    List<String> findAllExternalIds(@Param("portal") SourcePortal portal);

    List<Tender> findByClosingAtAfter(LocalDateTime cutoff);

    long countBySourcePortal(SourcePortal portal);
}
