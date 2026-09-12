package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.dto.RunSummaryResponse;
import com.bracit.tendersense.dto.ScheduleResponse;

/** Read-only views of collection runs and the schedule, for the admin screens. */
public interface PipelineOverviewService {

    /** Runs newest first. {@code size} is clamped to 1..100. */
    PageResponse<PipelineRunResponse> runs(int page, int size);

    RunSummaryResponse summary();

    ScheduleResponse schedule();
}
