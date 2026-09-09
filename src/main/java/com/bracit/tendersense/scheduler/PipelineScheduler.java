package com.bracit.tendersense.scheduler;

import com.bracit.tendersense.dto.DigestResponse;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.entity.enums.RunStatus;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.service.PipelineService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The scheduled pipeline.
 *
 * <p>Disabled wholesale by {@code tendersense.schedule.enabled=false}, which the
 * {@code demo} profile sets — a reconcile starting mid-presentation and holding the
 * database is an avoidable way to lose.
 *
 * <p>Every cron is pinned to {@code Asia/Dhaka} rather than server-local time. Without
 * that the 08:00 digest drifts to whatever timezone the host happens to run in, which
 * is the sort of bug nobody notices until the shortlist arrives at 2am.
 *
 * <h2>Why four jobs and not the six originally planned</h2>
 * <ul>
 *   <li><b>No separate detail-drain.</b> {@code EgpTenderFetchServiceImpl} already
 *       fetches detail pages inline for exactly the ids discovery found, rate limited
 *       to 1/sec. A queue plus a drain job would add moving parts for the same result.</li>
 *   <li><b>No urgency-refresh.</b> Days-to-deadline is derived on read in
 *       {@code TenderMapper}, not stored, so there is nothing to refresh. A job that
 *       recomputes a derived value is a no-op dressed as diligence.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "tendersense.schedule.enabled", havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PipelineScheduler {

    /**
     * After this many consecutive failures a job stops trying until one succeeds or
     * the application restarts. Hammering a government portal that is already failing
     * helps nobody and risks the access the whole system depends on.
     */
    private static final int FAILURE_LIMIT = 3;

    private final PipelineService pipelineService;

    @Value("${tendersense.schedule.zone}")
    private String zone;
    @Value("${tendersense.schedule.egp-discovery}")
    private String egpDiscoveryCron;
    @Value("${tendersense.schedule.egp-reconcile}")
    private String egpReconcileCron;
    @Value("${tendersense.schedule.world-bank-sync}")
    private String worldBankCron;
    @Value("${tendersense.schedule.morning-digest}")
    private String digestCron;

    private final AtomicInteger egpFailures = new AtomicInteger();
    private final AtomicInteger worldBankFailures = new AtomicInteger();

    /**
     * States what is armed, so "is the scheduler on?" is answerable from the log
     * rather than by waiting to see whether something fires.
     */
    @PostConstruct
    void announce() {
        log.info("scheduler ARMED ({}): egpDiscovery [{}] · worldBankSync [{}] · "
                        + "egpReconcile [{}] · morningDigest [{}]",
                zone, egpDiscoveryCron, worldBankCron, egpReconcileCron, digestCron);
    }

    /**
     * Cheap incremental sweep during Bangladesh business hours. Reads 1-3 listing pages
     * and stops once it has seen 20 consecutive known ids.
     *
     * <p>Bangladesh's government weekend is Friday-Saturday, so yield those days is near
     * zero. The job still runs: the cost is trivial and it catches off-cycle publications.
     */
    @Scheduled(cron = "${tendersense.schedule.egp-discovery}",
            zone = "${tendersense.schedule.zone}")
    public void egpDiscovery() {
        if (tripped(egpFailures, "egpDiscovery")) {
            return;
        }
        run("egpDiscovery", egpFailures,
                () -> pipelineService.runSource(SourcePortal.EGP_BANGLADESH, false));
    }

    /** World Bank is a JSON API, so this costs seconds and can run around the clock. */
    @Scheduled(cron = "${tendersense.schedule.world-bank-sync}",
            zone = "${tendersense.schedule.zone}")
    public void worldBankSync() {
        if (tripped(worldBankFailures, "worldBankSync")) {
            return;
        }
        run("worldBankSync", worldBankFailures,
                () -> pipelineService.runSource(SourcePortal.WORLD_BANK, false));
    }

    /**
     * Nightly full crawl. Discovery only ever sees <em>new</em> tenders, so this is the
     * only thing that picks up corrigenda on tenders already stored, status transitions,
     * anything missed during an outage, and re-parses after a parser-version bump.
     * Roughly an hour at the 1 req/sec politeness limit.
     */
    @Scheduled(cron = "${tendersense.schedule.egp-reconcile}",
            zone = "${tendersense.schedule.zone}")
    public void egpReconcile() {
        run("egpReconcile", egpFailures,
                () -> pipelineService.runSource(SourcePortal.EGP_BANGLADESH, true));
    }

    /** The BRD deliverable: the shortlist the BD team opens at the start of the day. */
    @Scheduled(cron = "${tendersense.schedule.morning-digest}",
            zone = "${tendersense.schedule.zone}")
    public void morningDigest() {
        try {
            DigestResponse digest = pipelineService.digest();
            log.info("morning digest {}: {} open tenders, top {} — {} S-grade, {} A-grade, "
                            + "{} closing within 7 days, {} need verification",
                    digest.date(), digest.openTenders(), digest.top().size(),
                    digest.sGrade(), digest.aGrade(),
                    digest.closingWithinSevenDays(), digest.needingVerification());
        } catch (Exception e) {
            // A failed digest must never take the application down with it.
            log.error("morning digest failed", e);
        }
    }

    // ---------------------------------------------------------------- internals

    private void run(String job, AtomicInteger failures,
                     java.util.function.Supplier<PipelineRunResponse> work) {
        Instant started = Instant.now();
        try {
            PipelineRunResponse result = work.get();

            if (result.status() == RunStatus.FAILED) {
                int n = failures.incrementAndGet();
                log.warn("{} failed ({} consecutive): {}", job, n, result.errorMessage());
                return;
            }
            if (result.status() == RunStatus.SKIPPED) {
                // Not a failure: another job held the lock. Leave the counter alone.
                log.info("{} skipped — another pipeline job was running", job);
                return;
            }

            failures.set(0);
            log.info("{} ok in {}ms — discovered {}, scored {}", job,
                    java.time.Duration.between(started, Instant.now()).toMillis(),
                    result.tendersDiscovered(), result.tendersScored());
        } catch (Exception e) {
            int n = failures.incrementAndGet();
            log.error("{} threw ({} consecutive)", job, n, e);
        }
    }

    private boolean tripped(AtomicInteger failures, String job) {
        if (failures.get() >= FAILURE_LIMIT) {
            log.warn("{} paused after {} consecutive failures — will resume once a "
                    + "reconcile or manual run succeeds", job, failures.get());
            return true;
        }
        return false;
    }
}
