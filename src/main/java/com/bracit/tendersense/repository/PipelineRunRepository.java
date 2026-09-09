package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.enums.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {

    List<PipelineRun> findTop20ByOrderByStartedAtDesc();

    boolean existsByJobNameAndStatus(String jobName, RunStatus status);

    List<PipelineRun> findByStatus(RunStatus status);
}
