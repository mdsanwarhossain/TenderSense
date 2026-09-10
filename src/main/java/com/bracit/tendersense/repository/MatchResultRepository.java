package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.LlmReviewStatus;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.time.Instant;
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

    /** Newest score for a company -- compared against the profile's updatedAt. */
    @Query("select max(m.computedAt) from MatchResult m where m.organisation.id = :orgId")
    Instant lastComputedAt(@Param("orgId") Long organisationId);

    long countByOrganisationIdAndMatcherType(Long organisationId, MatcherType matcherType);

    void deleteByOrganisationId(Long organisationId);

    // ---- LLM review: targeted updates only. Never save a whole MatchResult from the
    // ---- review job -- see @DynamicUpdate on the entity for why.

    @Modifying
    @Transactional
    @Query("update MatchResult m set m.llmStatus = :status where m.id in :ids")
    int markLlmStatus(@Param("ids") Collection<Long> ids, @Param("status") LlmReviewStatus status);

    @Modifying
    @Transactional
    @Query("update MatchResult m set m.llmScore = :score, m.llmReasoning = :reasoning, "
            + "m.llmStatus = :status, m.llmModel = :model, m.llmInputHash = :hash, "
            + "m.llmError = null, m.llmScoredAt = :at, m.llmDurationMs = :ms where m.id = :id")
    int recordLlmVerdict(@Param("id") Long id, @Param("score") int score,
                         @Param("reasoning") String reasoning, @Param("status") LlmReviewStatus status,
                         @Param("model") String model, @Param("hash") String hash,
                         @Param("at") Instant at, @Param("ms") long ms);

    @Modifying
    @Transactional
    @Query("update MatchResult m set m.llmStatus = :status, m.llmError = :error, "
            + "m.llmModel = :model, m.llmInputHash = :hash, m.llmScoredAt = :at, "
            + "m.llmDurationMs = :ms where m.id = :id")
    int recordLlmFailure(@Param("id") Long id, @Param("status") LlmReviewStatus status,
                         @Param("error") String error, @Param("model") String model,
                         @Param("hash") String hash, @Param("at") Instant at, @Param("ms") long ms);

    /**
     * A company's SCORED verdicts with what is needed to recompute their fingerprint:
     * {@code [matchId, storedHash, tenderId, tenderContentHash]}.
     */
    @Query("select m.id, m.llmInputHash, t.id, t.contentHash from MatchResult m join m.tender t "
            + "where m.organisation.id = :orgId and m.matcherType = :type and m.llmStatus = :status")
    List<Object[]> findLlmRows(@Param("orgId") Long organisationId,
                               @Param("type") MatcherType type,
                               @Param("status") LlmReviewStatus status);

    /** Pending rows that already hold an older verdict go back to STALE. */
    @Modifying
    @Transactional
    @Query("update MatchResult m set m.llmStatus = :stale "
            + "where m.llmStatus = :pending and m.llmScore is not null")
    int revertPendingWithVerdict(@Param("pending") LlmReviewStatus pending,
                                 @Param("stale") LlmReviewStatus stale);

    /** Pending rows that were never scored go back to never-reviewed. */
    @Modifying
    @Transactional
    @Query("update MatchResult m set m.llmStatus = null "
            + "where m.llmStatus = :pending and m.llmScore is null")
    int revertPendingWithoutVerdict(@Param("pending") LlmReviewStatus pending);
}
