package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.dto.RunSummaryResponse;
import com.bracit.tendersense.dto.ScheduleResponse;
import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.enums.RunStatus;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.service.JobScheduleService;
import com.bracit.tendersense.service.PipelineOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PipelineOverviewServiceImpl implements PipelineOverviewService {

    private final PipelineRunRepository runRepository;
    private final JobScheduleService jobScheduleService;

    @Override
    public PageResponse<PipelineRunResponse> runs(int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return PageResponse.of(runRepository.findAll(request), PipelineOverviewServiceImpl::toDto);
    }

    @Override
    public RunSummaryResponse summary() {
        return new RunSummaryResponse(
                runRepository.count(),
                runRepository.countByStatus(RunStatus.FAILED),
                runRepository.countByStatusAndStartedAtAfter(RunStatus.FAILED, Instant.now().minus(Duration.ofHours(24))),
                runRepository.findFirstByStatusOrderByStartedAtDesc(RunStatus.SUCCESS)
                        .map(PipelineRun::getStartedAt).orElse(null),
                runRepository.sumTendersScored());
    }

    /** The schedules as they are set now -- the ones the Scheduler page edits. */
    @Override
    public ScheduleResponse schedule() {
        return jobScheduleService.schedule();
    }

    static PipelineRunResponse toDto(PipelineRun r) {
        return new PipelineRunResponse(r.getId(), r.getJobName(), r.getStatus(),
                r.getStartedAt(), r.getFinishedAt(), r.getDurationMs(),
                r.getTendersDiscovered(), r.getTendersDetailed(), r.getTendersScored(),
                r.getErrorMessage());
    }
}
