package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.AiStatus;
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

    /**
     * The whole corpus for the admin list, newest first -- every tender collected, with no
     * company in the picture. Each filter is skipped when its parameter is null.
     *
     * @param search lower-case, already wrapped in % by the caller
     */
    @Query("""
           select t from Tender t
           where (:portal is null or t.sourcePortal = :portal)
             and (:aiStatus is null or t.aiStatus = :aiStatus)
             and (:includeClosed = true or t.closingAt is null or t.closingAt >= :now)
             and (:search is null
                  or lower(coalesce(t.aiShortTitle, '')) like :search
                  or lower(coalesce(t.title, '')) like :search
                  or lower(coalesce(t.buyer, '')) like :search
                  or lower(coalesce(t.referenceNo, '')) like :search
                  or lower(t.externalId) like :search)
           order by coalesce(t.publishedAt, t.closingAt) desc nulls last, t.id desc
           """)
    org.springframework.data.domain.Page<Tender> findForAdmin(@Param("portal") SourcePortal portal,
                                                              @Param("aiStatus") AiStatus aiStatus,
                                                              @Param("includeClosed") boolean includeClosed,
                                                              @Param("search") String search,
                                                              @Param("now") LocalDateTime now,
                                                              org.springframework.data.domain.Pageable pageable);
}
