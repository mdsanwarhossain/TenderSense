package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.enums.MatcherType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.bracit.tendersense.entity.enums.MatchGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    Optional<MatchResult> findByTenderIdAndMatcherType(Long tenderId, MatcherType matcherType);

    Page<MatchResult> findByMatcherTypeOrderByScoreDesc(MatcherType matcherType, Pageable pageable);

    /** Top-k for one matcher, restricted to a tender id set -- the benchmark's core query. */
    @Query("""
           select m from MatchResult m
           where m.matcherType = :matcherType and m.tender.id in :tenderIds
           order by m.score desc
           """)
    List<MatchResult> findTopForMatcher(@Param("matcherType") MatcherType matcherType,
                                        @Param("tenderIds") List<Long> tenderIds,
                                        Pageable pageable);

    /** Full score distribution, so calibration sees the whole corpus, not one batch. */
    @Query("select m.score from MatchResult m where m.matcherType = :matcherType and m.score > 0")
    List<Double> findScoresByMatcherType(@Param("matcherType") MatcherType matcherType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update MatchResult m set m.grade = :grade
           where m.matcherType = :matcherType and m.score >= :lower and m.score < :upper
           """)
    int applyGrade(@Param("matcherType") MatcherType matcherType,
                   @Param("grade") MatchGrade grade,
                   @Param("lower") double lower,
                   @Param("upper") double upper);

    /** One query for a whole page, instead of one lookup per row. */
    @Query("select m from MatchResult m where m.matcherType = :matcherType and m.tender.id in :tenderIds")
    List<MatchResult> findByMatcherTypeAndTenderIds(@Param("matcherType") MatcherType matcherType,
                                                    @Param("tenderIds") java.util.Collection<Long> tenderIds);

    /**
     * The shortlist query: ranked by score, filtered server-side.
     *
     * <p>Paging the tender table by publish date and then filtering by grade in the
     * browser produces "the newest tenders that happen to be A-grade", not the best
     * matches -- which is the opposite of what a shortlist is for.
     *
     * <p>Closed tenders are excluded by default. A high-scoring notice whose deadline
     * passed months ago is not actionable, and the ranking will happily surface one:
     * the corpus keeps historical World Bank notices alongside live ones. Tenders with
     * no stated closing date are kept and shown as "not stated" rather than dropped.
     */
    @Query("""
           select m from MatchResult m
           where m.matcherType = :matcherType
             and (:grade is null or m.grade = :grade)
             and (:source is null or m.tender.sourcePortal = :source)
             and (:includeClosed = true
                  or m.tender.closingAt is null
                  or m.tender.closingAt >= :now)
           order by m.score desc
           """)
    org.springframework.data.domain.Page<MatchResult> findRanked(
            @Param("matcherType") MatcherType matcherType,
            @Param("grade") MatchGrade grade,
            @Param("source") com.bracit.tendersense.entity.enums.SourcePortal source,
            @Param("includeClosed") boolean includeClosed,
            @Param("now") java.time.LocalDateTime now,
            Pageable pageable);

    void deleteByMatcherType(MatcherType matcherType);
}
