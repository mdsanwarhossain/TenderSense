package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.ScheduleProperties;
import com.bracit.tendersense.dto.CronPreview;
import com.bracit.tendersense.dto.ScheduleResponse;
import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.ScheduledJobSetting;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.repository.ScheduledJobSettingRepository;
import com.bracit.tendersense.scheduler.ScheduledJob;
import com.bracit.tendersense.scheduler.ScheduledJobRunner;
import com.bracit.tendersense.service.JobScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * Arms each scheduled job on Spring's TaskScheduler from its row in {@code schedule_job},
 * and re-arms it the moment an admin changes it. A run already under way when a job is
 * switched off or moved finishes normally; only future runs change.
 *
 * <p>Every row is created from the {@code tendersense.schedule.*} defaults the first time
 * the application starts on a database. Nothing is armed when scheduling is off on this
 * server ({@code tendersense.schedule.enabled=false}): the settings can still be read and
 * changed, and apply wherever it is on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobScheduleServiceImpl implements JobScheduleService {

    /** How many future runs a preview lists. */
    static final int PREVIEW_RUNS = 5;
    /** Consecutive runs checked against a job's minimum gap. */
    private static final int GAP_CHECKS = 60;

    private final ScheduledJobSettingRepository settings;
    private final PipelineRunRepository runRepository;
    private final ScheduleProperties props;
    private final TaskScheduler taskScheduler;
    private final Optional<ScheduledJobRunner> runner;

    private final Map<ScheduledJob, ScheduledFuture<?>> armed = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        seedMissing();
        if (runner.isEmpty()) {
            log.info("scheduler OFF on this server (tendersense.schedule.enabled=false): nothing is armed");
            return;
        }
        for (ScheduledJob job : ScheduledJob.values()) {
            arm(job, setting(job));
        }
        log.info("scheduler ARMED ({}): {}", zone(), Arrays.stream(ScheduledJob.values())
                .map(j -> j.key() + (armed.containsKey(j) ? " [" + setting(j).getCron() + "]" : " [off]"))
                .collect(Collectors.joining(" · ")));
    }

    @Override
    public ScheduleResponse schedule() {
        Map<String, ScheduledJobSetting> byKey = settings.findAll().stream()
                .collect(Collectors.toMap(ScheduledJobSetting::getJobKey, s -> s));
        List<ScheduleResponse.Job> jobs = Arrays.stream(ScheduledJob.values())
                .map(j -> view(j, byKey.getOrDefault(j.key(), defaults(j))))
                .toList();
        return new ScheduleResponse(runner.isPresent(), zone().getId(), jobs);
    }

    @Override
    public ScheduleResponse.Job update(String key, Boolean enabled, String cron, String actor) {
        ScheduledJob job = require(key);
        ScheduledJobSetting s = setting(job);
        if (cron != null) {
            String normalised = normalise(cron);
            validate(job, normalised, zone());
            s.setCron(normalised);
        }
        if (enabled != null) {
            s.setEnabled(enabled);
        }
        return save(job, s, actor);
    }

    @Override
    public ScheduleResponse.Job reset(String key, String actor) {
        ScheduledJob job = require(key);
        ScheduledJobSetting s = setting(job);
        s.setCron(job.defaultCron(props));
        s.setEnabled(true);
        return save(job, s, actor);
    }

    @Override
    public CronPreview preview(String key, String cron) {
        ScheduledJob job = require(key);
        String normalised = normalise(cron);
        try {
            validate(job, normalised, zone());
        } catch (IllegalArgumentException e) {
            return new CronPreview(normalised, false, e.getMessage(), List.of());
        }
        return new CronPreview(normalised, true, null, nextRuns(normalised, zone(), PREVIEW_RUNS));
    }

    // ---------------------------------------------------------------- rules

    /**
     * Spring cron has six fields, seconds first. A five-field one, as most cron guides
     * write it, gains a leading "0" rather than being refused.
     */
    static String normalise(String cron) {
        String c = cron == null ? "" : cron.strip().replaceAll("\\s+", " ");
        return c.split(" ").length == 5 ? "0 " + c : c;
    }

    /** Refuses a schedule that is not valid, never runs, or runs more often than the job allows. */
    static void validate(ScheduledJob job, String cron, ZoneId zone) {
        if (cron == null || cron.isBlank() || !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("That is not a valid schedule. Use six fields -- second, minute, "
                    + "hour, day, month, weekday -- for example \"0 10 8-20 * * *\" for ten past every hour, 08:00 to 20:00.");
        }
        CronExpression expr = CronExpression.parse(cron);
        ZonedDateTime previous = expr.next(ZonedDateTime.now(zone));
        if (previous == null) {
            throw new IllegalArgumentException("That schedule never runs.");
        }
        for (int i = 0; i < GAP_CHECKS; i++) {
            ZonedDateTime next = expr.next(previous);
            if (next == null) {
                return;
            }
            if (Duration.between(previous, next).compareTo(job.minGap()) < 0) {
                throw new IllegalArgumentException(job.label() + " can run at most once every " + human(job.minGap())
                        + ". More often would put too much load on the portal.");
            }
            previous = next;
        }
    }

    static List<Instant> nextRuns(String cron, ZoneId zone, int count) {
        CronExpression expr = CronExpression.parse(cron);
        List<Instant> out = new ArrayList<>(count);
        ZonedDateTime t = ZonedDateTime.now(zone);
        for (int i = 0; i < count; i++) {
            t = expr.next(t);
            if (t == null) {
                break;
            }
            out.add(t.toInstant());
        }
        return out;
    }

    private static String human(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 60) {
            return minutes + " minutes";
        }
        long hours = d.toHours();
        return hours == 1 ? "hour" : hours + " hours";
    }

    // ---------------------------------------------------------------- internals

    private ScheduleResponse.Job save(ScheduledJob job, ScheduledJobSetting s, String actor) {
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(actor);
        settings.save(s);
        runner.ifPresent(r -> r.clearFailures(job));
        arm(job, s);
        log.info("schedule of {} set to {} [{}] by {}", job.key(), s.isEnabled() ? "on" : "off", s.getCron(), actor);
        return view(job, s);
    }

    /** Cancels whatever is armed for the job, then arms it again if it is on. */
    private synchronized void arm(ScheduledJob job, ScheduledJobSetting s) {
        ScheduledFuture<?> previous = armed.remove(job);
        if (previous != null) {
            previous.cancel(false);
        }
        if (runner.isEmpty() || !s.isEnabled()) {
            return;
        }
        ScheduledJobRunner r = runner.get();
        String cron = s.getCron();
        if (!CronExpression.isValidExpression(cron)) {
            // Only reachable by editing the table by hand: keep the job running on its default.
            log.error("schedule of {} is not a valid cron [{}]; running it on its default", job.key(), cron);
            cron = job.defaultCron(props);
        }
        armed.put(job, taskScheduler.schedule(() -> fire(job, r), new CronTrigger(cron, zone())));
    }

    /** Records the firing first, so "last fire" holds even for a run that is skipped or paused. */
    private void fire(ScheduledJob job, ScheduledJobRunner r) {
        try {
            settings.markFired(job.key(), Instant.now());
        } catch (RuntimeException e) {
            // Bookkeeping only: never let it stop the job itself.
            log.warn("could not record the firing of {}: {}", job.key(), e.getMessage());
        }
        r.run(job);
    }

    private void seedMissing() {
        for (ScheduledJob job : ScheduledJob.values()) {
            if (settings.findById(job.key()).isPresent()) {
                continue;
            }
            ScheduledJobSetting s = defaults(job);
            if (s.getCron() == null) {
                log.warn("no default schedule for {} (tendersense.schedule.*); left unset", job.key());
                continue;
            }
            settings.save(s);
        }
    }

    private ScheduledJobSetting setting(ScheduledJob job) {
        return settings.findById(job.key()).orElseGet(() -> defaults(job));
    }

    private ScheduledJobSetting defaults(ScheduledJob job) {
        return ScheduledJobSetting.builder().jobKey(job.key()).cron(job.defaultCron(props)).enabled(true).build();
    }

    private ScheduleResponse.Job view(ScheduledJob job, ScheduledJobSetting s) {
        String runName = job.runJobName();
        Optional<PipelineRun> last = runName == null
                ? Optional.empty()
                : runRepository.findFirstByJobNameOrderByStartedAtDesc(runName);
        Double avg = runName == null ? null : runRepository.averageDurationMs(runName);
        Instant next = armed.containsKey(job) && CronExpression.isValidExpression(s.getCron())
                ? nextRuns(s.getCron(), zone(), 1).stream().findFirst().orElse(null)
                : null;
        return new ScheduleResponse.Job(job.key(), job.label(), job.description(),
                s.getCron(), job.defaultCron(props), s.isEnabled(),
                runner.map(r -> r.paused(job)).orElse(false),
                next,
                s.getLastFiredAt() != null ? s.getLastFiredAt() : last.map(PipelineRun::getStartedAt).orElse(null),
                last.map(PipelineRun::getStartedAt).orElse(null),
                last.map(PipelineRun::getStatus).orElse(null),
                avg == null ? null : Math.round(avg),
                s.getUpdatedAt(), s.getUpdatedBy());
    }

    private static ScheduledJob require(String key) {
        return ScheduledJob.byKey(key).orElseThrow(() -> new NotFoundException("no scheduled job called " + key));
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(props.getZone());
        } catch (RuntimeException e) {
            return ZoneId.of("Asia/Dhaka");
        }
    }
}
