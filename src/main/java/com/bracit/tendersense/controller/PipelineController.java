package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.DigestResponse;
import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.dto.ProcessingStatusResponse;
import com.bracit.tendersense.dto.RunSummaryResponse;
import com.bracit.tendersense.dto.ScheduleResponse;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.service.PipelineOverviewService;
import com.bracit.tendersense.service.PipelineService;
import com.bracit.tendersense.service.TenderProcessingService;
import com.bracit.tendersense.service.TenderStagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Manual pipeline triggers and run telemetry.
 *
 * <p>Admin only (SecurityConfig), except {@code /digest} and {@code /rescore}: those act on
 * the signed-in company's own tenders and stay with company accounts.
 *
 * <p>Thin by design: every run goes through {@link PipelineService}, the same path the
 * scheduler uses, so a manual run and a scheduled one cannot diverge.
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineOverviewService overviewService;
    private final TenderStagingService stagingService;
    private final TenderProcessingService processingService;

    /**
     * Queues every stored tender that has not been through the current pipeline, for the
     * worker to process -- the one-off backlog run. e-GP pages are re-parsed from their
     * saved snapshots on the way, so parser fixes reach old tenders too.
     */
    @PostMapping("/processing/backfill")
    public Map<String, Integer> backfill() {
        return Map.of("queued", stagingService.backfill());
    }

    /** The staging queue: how much is waiting, how fast the model is going, the last error. */
    @GetMapping("/processing")
    public ProcessingStatusResponse processing() {
        return processingService.status();
    }

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
    public PipelineRunResponse rescore(@CurrentOrganisation Organisation organisation) {
        return pipelineService.rescore(organisation);
    }

    /**
     * Re-scores every company. Separate from the button above so that one company's
     * profile edit cannot spend everyone else's compute.
     */
    @PostMapping("/rescore-all")
    public PipelineRunResponse rescoreAll() {
        return pipelineService.rescoreAll();
    }

    /** What the 08:00 Asia/Dhaka digest reports, on demand. */
    @GetMapping("/digest")
    public DigestResponse digest(@CurrentOrganisation Organisation organisation) {
        return pipelineService.digest(organisation);
    }

    /** Collection runs, newest first, a page at a time. */
    @GetMapping("/runs")
    public PageResponse<PipelineRunResponse> runs(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return overviewService.runs(page, size);
    }

    /** Totals over all runs, for the stat cards above the paged table. */
    @GetMapping("/runs/summary")
    public RunSummaryResponse runSummary() {
        return overviewService.summary();
    }

    /** The schedule as configured, with each job's next and last run. */
    @GetMapping("/schedule")
    public ScheduleResponse schedule() {
        return overviewService.schedule();
    }
}
