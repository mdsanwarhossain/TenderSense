package com.bracit.tendersense.controller;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.RunStatus;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.service.PipelineLock;
import com.bracit.tendersense.service.ScoringService;
import com.bracit.tendersense.service.TenderFetchService;
import com.bracit.tendersense.service.TenderIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Manual pipeline trigger and run telemetry.
 *
 * <p>The manual trigger is what the demo uses: the demo profile disables every
 * scheduled job so nothing fires mid-presentation.
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
@Slf4j
public class PipelineController {

    private final List<TenderFetchService> fetchServices;
    private final TenderIngestionService ingestionService;
    private final ScoringService scoringService;
    private final PipelineRunRepository runRepository;
    private final PipelineLock pipelineLock;

    /**
     * @param full when true, runs the full reconcile crawl instead of an incremental
     *             sweep. Reconcile re-reads tenders already stored, which is how
     *             corrigenda and parser-version bumps get picked up -- an incremental
     *             discovery skips known ids by design and would never see them.
     */
    @PostMapping("/run")
    public List<PipelineRunResponse> run(@RequestParam(defaultValue = "false") boolean full) {
        return pipelineLock
                .runExclusively(full ? "reconcile" : "discovery",
                        () -> fetchServices.stream().map(s -> runOne(s, full)).toList())
                .orElseGet(() -> List.of(busy(full ? "reconcile" : "discovery")));
    }

    /** A run rejected because another was already active is recorded, not silently dropped. */
    private PipelineRunResponse busy(String jobName) {
        Instant now = Instant.now();
        PipelineRun skipped = runRepository.save(PipelineRun.builder()
                .jobName(jobName).status(RunStatus.SKIPPED)
                .startedAt(now).finishedAt(now).durationMs(0L)
                .errorMessage("another pipeline job (" + pipelineLock.currentHolder()
                        + ") was already running")
                .build());
        return toDto(skipped);
    }

    /**
     * Re-scores every stored tender. Needed after a capability-profile edit or an
     * embedding-model change, since both invalidate previously stored scores.
     */
    @PostMapping("/rescore")
    public PipelineRunResponse rescore() {
        return pipelineLock.runExclusively("rescore", this::doRescore)
                .orElseGet(() -> busy("rescore"));
    }

    private PipelineRunResponse doRescore() {
        Instant started = Instant.now();
        PipelineRun run = runRepository.save(PipelineRun.builder()
                .jobName("rescore:ALL").status(RunStatus.RUNNING).startedAt(started).build());
        try {
            run.setTendersScored(scoringService.rescoreEverything());
            run.setStatus(RunStatus.SUCCESS);
        } catch (Exception e) {
            log.error("rescore failed", e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(truncate(e.getMessage()));
        } finally {
            run.setFinishedAt(Instant.now());
            run.setDurationMs(Duration.between(started, run.getFinishedAt()).toMillis());
            run = runRepository.save(run);
        }
        return toDto(run);
    }

    @GetMapping("/runs")
    public List<PipelineRunResponse> recentRuns() {
        return runRepository.findTop20ByOrderByStartedAtDesc().stream().map(this::toDto).toList();
    }

    private PipelineRunResponse runOne(TenderFetchService source, boolean full) {
        Instant started = Instant.now();
        PipelineRun run = PipelineRun.builder()
                .jobName((full ? "reconcile:" : "manual:") + source.portal())
                .status(RunStatus.RUNNING)
                .startedAt(started)
                .build();
        run = runRepository.save(run);

        try {
            FetchResult fetched = full
                    ? source.fetchAll()
                    : source.discover(ingestionService.knownExternalIds(source.portal()));
            List<Tender> ingested = ingestionService.ingest(fetched);

            // Only new and revised tenders are scored: unchanged ones already have
            // results, and re-embedding them would waste the whole run's budget.
            int scored = scoringService.scoreAll(ingested);
            // Grades are comparative, so new scores shift the thresholds for everyone.
            if (scored > 0) {
                scoringService.recalibrateGrades();
            }

            run.setStatus(RunStatus.SUCCESS);
            run.setTendersDiscovered(fetched.count());
            run.setTendersDetailed(fetched.detailsFetched());
            run.setTendersScored(scored);
        } catch (Exception e) {
            log.error("manual run failed for {}", source.portal(), e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(truncate(e.getMessage()));
        } finally {
            run.setFinishedAt(Instant.now());
            run.setDurationMs(Duration.between(started, run.getFinishedAt()).toMillis());
            run = runRepository.save(run);
        }
        return toDto(run);
    }

    private PipelineRunResponse toDto(PipelineRun r) {
        return new PipelineRunResponse(r.getId(), r.getJobName(), r.getStatus(),
                r.getStartedAt(), r.getFinishedAt(), r.getDurationMs(),
                r.getTendersDiscovered(), r.getTendersDetailed(), r.getTendersScored(),
                r.getErrorMessage());
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 2048 ? s : s.substring(0, 2048);
    }
}
