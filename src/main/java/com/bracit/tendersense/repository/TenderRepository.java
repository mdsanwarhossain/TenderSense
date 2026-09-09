package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.Sector;
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

    /** Rows predating the classifier, or ones a classification change left behind. */
    List<Tender> findBySectorIsNull();

    long countBySectorIsNull();

    /** Tenders needing an embedding: never computed, or computed by a different model. */
    @Query("select t from Tender t where t.embedding is null or t.embeddingModel <> :model")
    List<Tender> findNeedingEmbedding(@Param("model") String model);

    @Query("select count(t) from Tender t where t.embedding is null or t.embeddingModel <> :model")
    long countNeedingEmbedding(@Param("model") String model);
    /**
     * How many tenders a company's sector gate admits -- what a re-score would cover.
     * OTHER is included for everyone, matching the gate in ScoringServiceImpl.
     */
    @Query("select count(t) from Tender t where t.sector is null "
            + "or t.sector = com.bracit.tendersense.entity.enums.Sector.OTHER "
            + "or t.sector in :sectors")
    long countInSectors(@Param("sectors") Collection<Sector> sectors);
}
