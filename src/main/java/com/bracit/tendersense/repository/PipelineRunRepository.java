package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.enums.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {

    boolean existsByJobNameAndStatus(String jobName, RunStatus status);

    List<PipelineRun> findByStatus(RunStatus status);

    long countByStatus(RunStatus status);

    long countByStatusAndStartedAtAfter(RunStatus status, Instant after);

    Optional<PipelineRun> findFirstByStatusOrderByStartedAtDesc(RunStatus status);

    Optional<PipelineRun> findFirstByJobNameOrderByStartedAtDesc(String jobName);

    @Query("select coalesce(sum(r.tendersScored), 0) from PipelineRun r")
    long sumTendersScored();

    /** Mean duration of finished runs of one job, in ms; null when it never finished. */
    @Query("select avg(r.durationMs) from PipelineRun r where r.jobName = :jobName and r.durationMs is not null")
    Double averageDurationMs(String jobName);
}
