package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.DigestResponse;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.*;
import com.bracit.tendersense.repository.EligibilityVerdictRepository;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.*;
import com.bracit.tendersense.util.TenderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineServiceImpl implements PipelineService {

    /** How many tenders the morning digest carries. */
    private static final int DIGEST_SIZE = 15;

    private final List<TenderFetchService> fetchServices;
    private final TenderIngestionService ingestionService;
    private final ScoringService scoringService;
    private final PipelineRunRepository runRepository;
    private final MatchResultRepository matchResultRepository;
    private final EligibilityVerdictRepository eligibilityRepository;
    private final TenderRepository tenderRepository;
    private final TenderMapper mapper;
    private final PipelineLock pipelineLock;

    @Override
    public List<PipelineRunResponse> runAll(boolean full) {
        String job = full ? "reconcile" : "discovery";
        return pipelineLock
                .runExclusively(job, () -> {
                    List<PipelineRunResponse> runs =
                            fetchServices.stream().map(s -> execute(s, full)).toList();
                    // Grades are comparative, so new scores shift the thresholds for
                    // everyone. Recalibrate once after the batch, not per source.
                    if (runs.stream().anyMatch(r -> (r.tendersScored() != null && r.tendersScored() > 0))) {
                        scoringService.recalibrateGrades();
                    }
                    return runs;
                })
                .orElseGet(() -> List.of(recordSkipped(job)));
    }

    @Override
    public PipelineRunResponse runSource(SourcePortal portal, boolean full) {
        Optional<TenderFetchService> source =
                fetchServices.stream().filter(s -> s.portal() == portal).findFirst();
        if (source.isEmpty()) {
            log.warn("no active fetch service for {}", portal);
            return recordSkipped("no source bean for " + portal);
        }
        String job = (full ? "reconcile:" : "discovery:") + portal;
        return pipelineLock
                .runExclusively(job, () -> {
                    PipelineRunResponse run = execute(source.get(), full);
                    if (run.tendersScored() != null && run.tendersScored() > 0) {
                        scoringService.recalibrateGrades();
                    }
                    return run;
                })
                .orElseGet(() -> recordSkipped(job));
    }

    @Override
    public PipelineRunResponse rescore() {
        return pipelineLock.runExclusively("rescore", this::doRescore)
                .orElseGet(() -> recordSkipped("rescore"));
    }

    @Override
    public DigestResponse digest() {
        Page<MatchResult> ranked = matchResultRepository.findRanked(
                MatcherType.EMBEDDING, null, null, false, LocalDateTime.now(),
                PageRequest.of(0, DIGEST_SIZE));

        List<Long> ids = ranked.getContent().stream().map(m -> m.getTender().getId()).toList();
        Map<Long, Tender> tenders = new HashMap<>();
        tenderRepository.findAllById(ids).forEach(t -> tenders.put(t.getId(), t));

        Map<Long, EligibilityVerdictView> verdicts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : eligibilityRepository.findSummariesByTenderIds(ids)) {
                verdicts.put(((Number) row[0]).longValue(),
                        new EligibilityVerdictView((EligibilityStatus) row[1],
                                ((Number) row[2]).intValue()));
            }
        }

        List<TenderSummaryResponse> top = ranked.getContent().stream()
                .map(m -> {
                    Tender t = tenders.get(m.getTender().getId());
                    return t == null ? null : mapper.toSummary(t, m, verdicts.get(t.getId()));
                })
                .filter(Objects::nonNull)
                .toList();

        return new DigestResponse(
                LocalDate.now(),
                ranked.getTotalElements(),
                (int) top.stream().filter(r -> r.grade() == MatchGrade.S).count(),
                (int) top.stream().filter(r -> r.grade() == MatchGrade.A).count(),
                (int) top.stream().filter(TenderSummaryResponse::urgent).count(),
                (int) top.stream()
                        .filter(r -> r.eligibility() == EligibilityStatus.NEEDS_VERIFICATION).count(),
                top);
    }

    // ---------------------------------------------------------------- internals

    private PipelineRunResponse execute(TenderFetchService source, boolean full) {
        Instant started = Instant.now();
        PipelineRun run = runRepository.save(PipelineRun.builder()
                .jobName((full ? "reconcile:" : "discovery:") + source.portal())
                .status(RunStatus.RUNNING)
                .startedAt(started)
                .build());

        try {
            FetchResult fetched = full
                    ? source.fetchAll()
                    : source.discover(ingestionService.knownExternalIds(source.portal()));

            // Only new and revised tenders are scored: unchanged ones already have
            // results, and re-embedding them would spend the whole run's budget.
            List<Tender> ingested = ingestionService.ingest(fetched);
            int scored = scoringService.scoreAll(ingested);

            run.setStatus(RunStatus.SUCCESS);
            run.setTendersDiscovered(fetched.count());
            run.setTendersDetailed(fetched.detailsFetched());
            run.setTendersScored(scored);
        } catch (Exception e) {
            log.error("pipeline run failed for {}", source.portal(), e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(truncate(e.getMessage()));
        } finally {
            run.setFinishedAt(Instant.now());
            run.setDurationMs(Duration.between(started, run.getFinishedAt()).toMillis());
            run = runRepository.save(run);
        }
        return toDto(run);
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

    /** A run rejected because another was active is recorded, never silently dropped. */
    private PipelineRunResponse recordSkipped(String jobName) {
        Instant now = Instant.now();
        return toDto(runRepository.save(PipelineRun.builder()
                .jobName(jobName)
                .status(RunStatus.SKIPPED)
                .startedAt(now)
                .finishedAt(now)
                .durationMs(0L)
                .errorMessage("another pipeline job (" + pipelineLock.currentHolder()
                        + ") was already running")
                .build()));
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
