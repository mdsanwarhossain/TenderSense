package com.bracit.tendersense.scheduler;

import com.bracit.tendersense.dto.DigestResponse;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.RunStatus;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * What each scheduled job does when it fires. When it fires is not decided here: the
 * schedules are in the database, armed by JobScheduleServiceImpl, so an admin can switch a
 * job off or move it from the Scheduler page without a restart.
 *
 * <p>Disabled wholesale by {@code tendersense.schedule.enabled=false}, which the
 * {@code demo} profile sets — a reconcile starting mid-presentation and holding the
 * database is an avoidable way to lose.
 *
 * <h2>Why no detail-drain or urgency-refresh job</h2>
 * <ul>
 *   <li><b>No separate detail-drain.</b> {@code EgpTenderFetchServiceImpl} already
 *       fetches detail pages inline for exactly the ids discovery found, rate limited
 *       to 1/sec. A queue plus a drain job would add moving parts for the same result.</li>
 *   <li><b>No urgency-refresh.</b> Days-to-deadline is derived on read in
 *       {@code TenderMapper}, not stored, so there is nothing to refresh.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "tendersense.schedule.enabled", havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PipelineScheduler implements ScheduledJobRunner {

    /**
     * After this many consecutive failures a job stops trying until one succeeds, an
     * admin changes it, or the application restarts. Hammering a government portal that
     * is already failing helps nobody and risks the access the whole system depends on.
     */
    static final int FAILURE_LIMIT = 3;

    private final PipelineService pipelineService;
    private final OrganisationRepository organisationRepository;

    // e-GP discovery and reconcile share one counter: a successful nightly reconcile is
    // what lets a paused discovery resume.
    private final AtomicInteger egpFailures = new AtomicInteger();
    private final AtomicInteger worldBankFailures = new AtomicInteger();
    private final AtomicInteger ungmFailures = new AtomicInteger();
    private final AtomicInteger isdbFailures = new AtomicInteger();
    private final AtomicInteger bracFailures = new AtomicInteger();

    @Override
    public void run(ScheduledJob job) {
        switch (job) {
            // Cheap incremental sweep during Bangladesh business hours: 1-3 listing pages,
            // stopping once it has seen 20 consecutive known ids.
            case EGP_DISCOVERY -> guarded(job, egpFailures, () -> pipelineService.runSource(SourcePortal.EGP_BANGLADESH, false));
            case WORLD_BANK_SYNC -> guarded(job, worldBankFailures, () -> pipelineService.runSource(SourcePortal.WORLD_BANK, false));
            case UNGM_SYNC -> guarded(job, ungmFailures, () -> pipelineService.runSource(SourcePortal.UNGM, false));
            case ISDB_SYNC -> guarded(job, isdbFailures, () -> pipelineService.runSource(SourcePortal.ISDB, false));
            case BRAC_SYNC -> guarded(job, bracFailures, () -> pipelineService.runSource(SourcePortal.BRAC, false));
            // Nightly full crawl: the only thing that sees corrigenda on stored tenders,
            // status transitions and anything missed during an outage. Never paused.
            case EGP_RECONCILE -> run(job, egpFailures, () -> pipelineService.runSource(SourcePortal.EGP_BANGLADESH, true));
            case MORNING_DIGEST -> morningDigest();
        }
    }

    @Override
    public boolean paused(ScheduledJob job) {
        AtomicInteger failures = counter(job);
        return job != ScheduledJob.EGP_RECONCILE && failures != null && failures.get() >= FAILURE_LIMIT;
    }

    @Override
    public void clearFailures(ScheduledJob job) {
        AtomicInteger failures = counter(job);
        if (failures != null) {
            failures.set(0);
        }
    }

    private AtomicInteger counter(ScheduledJob job) {
        return switch (job) {
            case EGP_DISCOVERY, EGP_RECONCILE -> egpFailures;
            case WORLD_BANK_SYNC -> worldBankFailures;
            case UNGM_SYNC -> ungmFailures;
            case ISDB_SYNC -> isdbFailures;
            case BRAC_SYNC -> bracFailures;
            case MORNING_DIGEST -> null;
        };
    }

    /** The BRD deliverable: the shortlist the BD team opens at the start of the day. */
    private void morningDigest() {
        try {
            // One digest per subscribing company — the corpus is shared, the shortlist is not.
            for (Organisation org : organisationRepository.findByActiveTrueOrderByIdAsc()) {
                DigestResponse digest = pipelineService.digest(org);
                log.info("morning digest {} for {}: {} open tenders, top {} — {} S-grade, "
                                + "{} A-grade, {} closing within 7 days, {} need verification",
                        digest.date(), org.getSlug(), digest.openTenders(), digest.top().size(),
                        digest.sGrade(), digest.aGrade(),
                        digest.closingWithinSevenDays(), digest.needingVerification());
            }
        } catch (Exception e) {
            // A failed digest must never take the application down with it.
            log.error("morning digest failed", e);
        }
    }

    // ---------------------------------------------------------------- internals

    private void guarded(ScheduledJob job, AtomicInteger failures, Supplier<PipelineRunResponse> work) {
        if (failures.get() >= FAILURE_LIMIT) {
            log.warn("{} paused after {} consecutive failures — will resume once a reconcile or "
                    + "manual run succeeds, or an admin changes the job", job.key(), failures.get());
            return;
        }
        run(job, failures, work);
    }

    private void run(ScheduledJob job, AtomicInteger failures, Supplier<PipelineRunResponse> work) {
        Instant started = Instant.now();
        try {
            PipelineRunResponse result = work.get();

            if (result.status() == RunStatus.FAILED) {
                int n = failures.incrementAndGet();
                log.warn("{} failed ({} consecutive): {}", job.key(), n, result.errorMessage());
                return;
            }
            if (result.status() == RunStatus.SKIPPED) {
                // Not a failure: another job held the lock. Leave the counter alone.
                log.info("{} skipped — another pipeline job was running", job.key());
                return;
            }

            failures.set(0);
            log.info("{} ok in {}ms — discovered {}, scored {}", job.key(),
                    java.time.Duration.between(started, Instant.now()).toMillis(),
                    result.tendersDiscovered(), result.tendersScored());
        } catch (Exception e) {
            int n = failures.incrementAndGet();
            log.error("{} threw ({} consecutive)", job.key(), n, e);
        }
    }
}
