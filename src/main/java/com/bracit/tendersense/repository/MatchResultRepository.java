package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every query here is scoped to one organisation. A score without a tenant is
 * meaningless once more than one company is subscribed.
 */
public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    Optional<MatchResult> findByTenderIdAndOrganisationIdAndMatcherType(
            Long tenderId, Long organisationId, MatcherType matcherType);

    /**
     * The shortlist query: ranked by score, filtered server-side, scoped to one company.
     *
     * <p>Closed tenders are excluded by default — a high-scoring notice whose deadline
     * passed months ago is not actionable, and the ranking will happily surface one.
     * Tenders with no stated closing date are kept and shown as "not stated".
     */
    @Query("""
           select m from MatchResult m
           where m.matcherType = :matcherType
             and m.organisation.id = :organisationId
             and (:grade is null or m.grade = :grade)
             and (:source is null or m.tender.sourcePortal = :source)
             and (:sector is null or m.tender.sector = :sector)
             and (:includeClosed = true
                  or m.tender.closingAt is null
                  or m.tender.closingAt >= :now)
           order by m.score desc
           """)
    Page<MatchResult> findRanked(@Param("matcherType") MatcherType matcherType,
                                 @Param("organisationId") Long organisationId,
                                 @Param("grade") MatchGrade grade,
                                 @Param("source") SourcePortal source,
                                 @Param("sector") Sector sector,
                                 @Param("includeClosed") boolean includeClosed,
                                 @Param("now") LocalDateTime now,
                                 Pageable pageable);

    /** One query for a whole page, instead of one lookup per row. */
    @Query("""
           select m from MatchResult m
           where m.matcherType = :matcherType
             and m.organisation.id = :organisationId
             and m.tender.id in :tenderIds
           """)
    List<MatchResult> findByMatcherTypeAndTenderIds(@Param("matcherType") MatcherType matcherType,
                                                    @Param("organisationId") Long organisationId,
                                                    @Param("tenderIds") Collection<Long> tenderIds);

    /** Distribution for calibration — per organisation, since thresholds are per organisation. */
    @Query("""
           select m.score from MatchResult m
           where m.matcherType = :matcherType
             and m.organisation.id = :organisationId
             and m.score > 0
           """)
    List<Double> findScoresByMatcherType(@Param("matcherType") MatcherType matcherType,
                                         @Param("organisationId") Long organisationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update MatchResult m set m.grade = :grade
           where m.matcherType = :matcherType
             and m.organisation.id = :organisationId
             and m.score >= :lower and m.score < :upper
           """)
    int applyGrade(@Param("matcherType") MatcherType matcherType,
                   @Param("organisationId") Long organisationId,
                   @Param("grade") MatchGrade grade,
                   @Param("lower") double lower,
                   @Param("upper") double upper);

    long countByOrganisationId(Long organisationId);

    void deleteByOrganisationId(Long organisationId);
}
