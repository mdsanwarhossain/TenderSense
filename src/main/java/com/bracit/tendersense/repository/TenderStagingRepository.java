package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.TenderStaging;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.StagingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenderStagingRepository extends JpaRepository<TenderStaging, Long> {

    /**
     * Closed tenders waiting: they skip the model, so they are claimed in bulk.
     * {@code skip locked} means two claimers can never take the same row.
     */
    @Query(value = """
            select * from tender_staging
            where status = 'PENDING' and closing_at < :now
            order by id
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<TenderStaging> lockClosed(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Open tenders waiting, soonest deadline first -- they are the ones that expire. */
    @Query(value = """
            select * from tender_staging
            where status = 'PENDING' and (closing_at is null or closing_at >= :now)
            order by closing_at asc nulls last, id
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<TenderStaging> lockOpen(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Rows a crashed batch left claimed go back into the queue. */
    @Modifying
    @Query("""
           update TenderStaging s set s.status = :pending, s.lockedAt = null
           where s.status = :processing and s.lockedAt < :before""")
    int releaseStale(@Param("pending") StagingStatus pending,
                     @Param("processing") StagingStatus processing,
                     @Param("before") Instant before);

    /** The row for exactly this version of a tender, if one was ever staged. */
    @Query("""
           select s from TenderStaging s
           where s.sourcePortal = :portal and s.externalId = :externalId
             and coalesce(s.contentHash, '') = :hash and coalesce(s.parserVersion, '') = :parser""")
    Optional<TenderStaging> findVersion(@Param("portal") SourcePortal portal,
                                        @Param("externalId") String externalId,
                                        @Param("hash") String hash,
                                        @Param("parser") String parser);

    /** Ids already on their way in, so discovery does not fetch them a second time. */
    @Query("select s.externalId from TenderStaging s where s.sourcePortal = :portal and s.status in :statuses")
    List<String> findExternalIds(@Param("portal") SourcePortal portal,
                                 @Param("statuses") Collection<StagingStatus> statuses);

    List<TenderStaging> findByStatus(StagingStatus status);

    long countByStatus(StagingStatus status);

    @Query("""
           select count(s) from TenderStaging s
           where s.status = :status and (s.closingAt is null or s.closingAt >= :now)""")
    long countOpen(@Param("status") StagingStatus status, @Param("now") LocalDateTime now);

    @Query("select count(s) from TenderStaging s where s.aiUsed = true and s.processedAt >= :since")
    long countModelReadsSince(@Param("since") Instant since);

    @Query("select avg(s.aiDurationMs) from TenderStaging s where s.aiUsed = true")
    Double averageModelMillis();
}
