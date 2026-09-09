package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.RunStatus;

import java.time.Instant;

public record PipelineRunResponse(
        Long id,
        String jobName,
        RunStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        Integer tendersDiscovered,
        Integer tendersDetailed,
        Integer tendersScored,
        String errorMessage) {
}
