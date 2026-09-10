package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.LlmMatchVerdict;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.LlmReviewStatus;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.RunStatus;
import com.bracit.tendersense.exception.LlmUnavailableException;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.LlmMatchScorer;
import com.bracit.tendersense.service.LlmReviewService;
import com.bracit.tendersense.util.LlmPromptBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class LlmReviewServiceImpl implements LlmReviewService {

    private final LlmProperties properties;
    private final LlmMatchScorer scorer;
    private final MatchResultRepository matchResultRepository;
    private final TenderRepository tenderRepository;
    private final OrganisationRepository organisationRepository;
    private final CapabilityProfileService profileService;
    private final PipelineRunRepository runRepository;
    private final TransactionTemplate readOnly;

    /** One worker: runs are serialised, and nothing else competes for a CPU-bound model. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "llm-review");
        t.setDaemon(true);
        return t;
    });
    /** Companies waiting in the queue, so a burst of triggers queues each one only once. */
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();

    public LlmReviewServiceImpl(LlmProperties properties,
                                LlmMatchScorer scorer,
                                MatchResultRepository matchResultRepository,
                                TenderRepository tenderRepository,
                                OrganisationRepository organisationRepository,
                                CapabilityProfileService profileService,
                                PipelineRunRepository runRepository,
                                PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.scorer = scorer;
        this.matchResultRepository = matchResultRepository;
        this.tenderRepository = tenderRepository;
        this.organisationRepository = organisationRepository;
        this.profileService = profileService;
        this.runRepository = runRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public boolean request(Organisation organisation) {
        if (!properties.isEnabled()) {
            return false;
        }
        Long id = organisation.getId();
        if (!queued.add(id)) {
            log.debug("{}: LLM review already queued", organisation.getSlug());
            return false;
        }
        worker.submit(() -> {
            // Leave the queue as the run *starts*, not when it ends: a profile edit that
            // lands mid-run must be able to queue a follow-up pass. The follow-up is cheap --
            // anything this run finishes is up to date and skipped.
            queued.remove(id);
            try {
                organisationRepository.findById(id)
                        .filter(Organisation::isActive)
                        .ifPresent(this::review);
            } catch (RuntimeException e) {
                log.error("LLM review for organisation {} crashed", id, e);
            }
        });
        return true;
    }

    @Override
    public void requestAll() {
        organisationRepository.findByActiveTrueOrderByIdAsc().forEach(this::request);
    }

    @Override
    public Outcome review(Organisation organisation) {
        Instant started = Instant.now();
        String job = "llm-review:" + organisation.getSlug();
        PipelineRun run = runRepository.save(PipelineRun.builder()
                .jobName(job).status(RunStatus.RUNNING).startedAt(started).build());

        Outcome outcome;
        try {
            outcome = doReview(organisation);
        } catch (RuntimeException e) {
            log.error("{}: LLM review failed", organisation.getSlug(), e);
            revertPending();
            outcome = new Outcome(0, 0, 0, 0, "crashed: " + e.getMessage());
        }

        boolean skipped = outcome.selected() == 0 && outcome.aborted() != null;
        run.setStatus(skipped ? RunStatus.SKIPPED
                : outcome.aborted() != null ? RunStatus.FAILED : RunStatus.SUCCESS);
        run.setTendersDiscovered(outcome.selected());
        run.setTendersDetailed(outcome.selected() - outcome.upToDate());
        run.setTendersScored(outcome.scored());
        run.setErrorMessage(outcome.aborted() != null ? truncate(outcome.aborted(), 2048)
                : outcome.failed() > 0 ? outcome.failed() + " tender(s) got no valid verdict" : null);
        run.setFinishedAt(Instant.now());
        run.setDurationMs(Duration.between(started, run.getFinishedAt()).toMillis());
        runRepository.save(run);
        return outcome;
    }

    private Outcome doReview(Organisation organisation) {
        String model = scorer.modelVersion();

        // Profile and prompt inside a read-only transaction: past projects are a lazy
        // collection, and this runs on a background thread with no session of its own.
        Prepared prepared = readOnly.execute(status -> prepare(organisation));
        if (prepared == null) {
            return Outcome.skipped("no service lines to compare against -- add some on the profile screen");
        }

        // 1. Stale sweep. A verdict is only current if everything it was computed from is
        //    unchanged; one fingerprint covers profile edits, tender revisions, and model or
        //    prompt changes. Runs before selection so the rows selected below see the result.
        List<Long> stale = new ArrayList<>();
        for (Object[] row : matchResultRepository.findLlmRows(
                organisation.getId(), MatcherType.EMBEDDING, LlmReviewStatus.SCORED)) {
            String current = LlmPromptBuilder.fingerprint(
                    (Long) row[2], (String) row[3], prepared.profileVersion(), model);
            if (!current.equals(row[1])) {
                stale.add((Long) row[0]);
            }
        }
        if (!stale.isEmpty()) {
            matchResultRepository.markLlmStatus(stale, LlmReviewStatus.STALE);
        }

        // 2. Page one: the exact query behind the shortlist, so "reviewed" means "the rows
        //    a bid manager sees first".
        List<MatchResult> page = matchResultRepository.findRanked(
                MatcherType.EMBEDDING, organisation.getId(), null, null, null, false,
                LocalDateTime.now(), PageRequest.of(0, properties.getTopN())).getContent();

        // Tenders loaded by id, never through MatchResult.getTender(): that association is
        // lazy and this thread has no open session, so touching it would throw.
        Map<Long, Tender> tenders = new HashMap<>();
        tenderRepository.findAllById(page.stream().map(m -> m.getTender().getId()).toList())
                .forEach(t -> tenders.put(t.getId(), t));

        // 3. Only rows not already scored against the current inputs cost a model call.
        List<Item> todo = new ArrayList<>();
        int upToDate = 0;
        for (MatchResult m : page) {
            Tender t = tenders.get(m.getTender().getId());
            if (t == null) {
                continue;
            }
            String fp = LlmPromptBuilder.fingerprint(t.getId(), t.getContentHash(),
                    prepared.profileVersion(), model);
            if (m.getLlmStatus() == LlmReviewStatus.SCORED && fp.equals(m.getLlmInputHash())) {
                upToDate++;
            } else {
                todo.add(new Item(m.getId(), t, fp));
            }
        }
        if (!todo.isEmpty()) {
            matchResultRepository.markLlmStatus(
                    todo.stream().map(Item::matchId).toList(), LlmReviewStatus.PENDING);
        }
        log.info("{}: LLM review -- {} on page one, {} up to date, {} to score, {} marked stale",
                organisation.getSlug(), page.size(), upToDate, todo.size(), stale.size());

        // 4. Score one at a time, each verdict committed on its own, so a crash or a stop
        //    part-way keeps everything already done.
        int scored = 0;
        int failed = 0;
        String aborted = null;
        long runStarted = System.currentTimeMillis();
        for (Item item : todo) {
            long t0 = System.currentTimeMillis();
            try {
                LlmMatchVerdict verdict = scorer.score(
                        prepared.systemPrompt(), LlmPromptBuilder.tenderPrompt(item.tender()));
                matchResultRepository.recordLlmVerdict(item.matchId(), verdict.matchScore(),
                        verdict.reasoning(), LlmReviewStatus.SCORED, model, item.fingerprint(),
                        Instant.now(), System.currentTimeMillis() - t0);
                scored++;
                log.debug("{}: tender {} -> {} in {}ms", organisation.getSlug(),
                        item.tender().getId(), verdict.matchScore(), System.currentTimeMillis() - t0);
            } catch (LlmUnavailableException e) {
                // Every remaining call would fail the same way. Stop now rather than make
                // twenty-odd more calls that each wait out a timeout.
                aborted = e.getMessage();
                break;
            } catch (RuntimeException e) {
                matchResultRepository.recordLlmFailure(item.matchId(), LlmReviewStatus.FAILED,
                        truncate(e.getMessage(), 512), model, item.fingerprint(),
                        Instant.now(), System.currentTimeMillis() - t0);
                failed++;
                log.warn("{}: no verdict for tender {}: {}", organisation.getSlug(),
                        item.tender().getId(), e.getMessage());
            }
            if (Thread.currentThread().isInterrupted()) {
                aborted = "interrupted";
                break;
            }
        }
        if (aborted != null) {
            // Nothing may be left saying "pending" for a review that is not going to happen.
            revertPending();
        }

        long ms = System.currentTimeMillis() - runStarted;
        log.info("{}: LLM review done -- {} scored, {} failed in {}s ({} s/tender){}",
                organisation.getSlug(), scored, failed, ms / 1000,
                scored + failed == 0 ? 0 : ms / 1000 / (scored + failed),
                aborted == null ? "" : " -- aborted: " + aborted);
        return new Outcome(page.size(), upToDate, scored, failed, aborted);
    }

    /** Null when the company has nothing to compare a tender against. */
    private Prepared prepare(Organisation organisation) {
        CapabilityProfile profile;
        try {
            profile = profileService.forOrganisation(organisation);
        } catch (RuntimeException e) {
            return null;
        }
        if (profile == null || profile.getServices() == null || profile.getServices().isEmpty()) {
            return null;
        }
        return new Prepared(LlmPromptBuilder.systemPrompt(organisation, profile), profile.getUpdatedAt());
    }

    private void revertPending() {
        matchResultRepository.revertPendingWithVerdict(LlmReviewStatus.PENDING, LlmReviewStatus.STALE);
        matchResultRepository.revertPendingWithoutVerdict(LlmReviewStatus.PENDING);
    }

    /** A restart mid-run leaves rows PENDING with no worker to finish them. */
    @EventListener(ApplicationReadyEvent.class)
    public void resetInterruptedReviews() {
        int withVerdict = matchResultRepository.revertPendingWithVerdict(
                LlmReviewStatus.PENDING, LlmReviewStatus.STALE);
        int without = matchResultRepository.revertPendingWithoutVerdict(LlmReviewStatus.PENDING);
        if (withVerdict + without > 0) {
            log.info("reset {} LLM review(s) left pending by a previous shutdown", withVerdict + without);
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private record Prepared(String systemPrompt, Instant profileVersion) {}

    private record Item(Long matchId, Tender tender, String fingerprint) {}
}
