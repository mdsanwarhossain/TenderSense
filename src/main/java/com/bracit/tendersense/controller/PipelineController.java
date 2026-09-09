package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.DigestResponse;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manual pipeline triggers and run telemetry.
 *
 * <p>Thin by design: every run goes through {@link PipelineService}, the same path the
 * scheduler uses, so a manual run and a scheduled one cannot diverge.
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineRunRepository runRepository;

    /**
     * @param full when true, runs the full reconcile crawl instead of an incremental
     *             sweep. Reconcile re-reads tenders already stored, which is how
     *             corrigenda and parser-version bumps get picked up — an incremental
     *             discovery skips known ids by design and would never see them.
     */
    @PostMapping("/run")
    public List<PipelineRunResponse> run(@RequestParam(defaultValue = "false") boolean full) {
        return pipelineService.runAll(full);
    }

    /**
     * Re-scores every stored tender. Needed after a capability-profile edit or an
     * embedding-model change, since both invalidate previously stored scores.
     */
    @PostMapping("/rescore")
    public PipelineRunResponse rescore() {
        return pipelineService.rescore();
    }

    /** What the 08:00 Asia/Dhaka digest reports, on demand. */
    @GetMapping("/digest")
    public DigestResponse digest(@CurrentOrganisation Organisation organisation) {
        return pipelineService.digest(organisation);
    }

    @GetMapping("/runs")
    public List<PipelineRunResponse> recentRuns() {
        return runRepository.findTop20ByOrderByStartedAtDesc().stream().map(this::toDto).toList();
    }

    private PipelineRunResponse toDto(PipelineRun r) {
        return new PipelineRunResponse(r.getId(), r.getJobName(), r.getStatus(),
                r.getStartedAt(), r.getFinishedAt(), r.getDurationMs(),
                r.getTendersDiscovered(), r.getTendersDetailed(), r.getTendersScored(),
                r.getErrorMessage());
    }
}
