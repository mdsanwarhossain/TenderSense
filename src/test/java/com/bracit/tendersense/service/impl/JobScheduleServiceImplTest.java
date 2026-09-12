package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.ScheduleProperties;
import com.bracit.tendersense.entity.ScheduledJobSetting;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.repository.ScheduledJobSettingRepository;
import com.bracit.tendersense.scheduler.ScheduledJob;
import com.bracit.tendersense.scheduler.ScheduledJobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Arming, re-arming and the rules a schedule has to pass. */
class JobScheduleServiceImplTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private final Map<String, ScheduledJobSetting> rows = new HashMap<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private ScheduledJobSettingRepository repo;
    private TaskScheduler taskScheduler;
    private ScheduledJobRunner runner;

    private static ScheduleProperties props() {
        ScheduleProperties p = new ScheduleProperties();
        p.setEgpDiscovery("0 0/30 8-20 * * *");
        p.setEgpReconcile("0 0 2 * * *");
        p.setWorldBankSync("0 15 */6 * * *");
        p.setUngmSync("0 30 */6 * * *");
        p.setIsdbSync("0 45 */6 * * *");
        p.setBracSync("0 10 8-20 * * *");
        p.setMorningDigest("0 0 8 * * *");
        return p;
    }

    @BeforeEach
    void setUp() {
        repo = mock(ScheduledJobSettingRepository.class);
        when(repo.findById(any())).thenAnswer(i -> Optional.ofNullable(rows.get((String) i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> {
            ScheduledJobSetting s = i.getArgument(0);
            rows.put(s.getJobKey(), s);
            return s;
        });
        when(repo.findAll()).thenAnswer(i -> new ArrayList<>(rows.values()));
        taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(i -> {
            ScheduledFuture<?> f = mock(ScheduledFuture.class);
            futures.add(f);
            return f;
        });
        runner = mock(ScheduledJobRunner.class);
    }

    private JobScheduleServiceImpl service(boolean schedulingOn) {
        return new JobScheduleServiceImpl(repo, mock(PipelineRunRepository.class), props(), taskScheduler,
                schedulingOn ? Optional.of(runner) : Optional.empty());
    }

    @Test
    @DisplayName("first start copies the defaults into the table and arms every job")
    void startSeedsAndArms() {
        JobScheduleServiceImpl service = service(true);
        service.start();

        assertEquals(ScheduledJob.values().length, rows.size());
        assertEquals("0 10 8-20 * * *", rows.get("bracSync").getCron());
        assertTrue(rows.get("bracSync").isEnabled());
        verify(taskScheduler, times(ScheduledJob.values().length)).schedule(any(Runnable.class), any(Trigger.class));
        assertTrue(service.schedule().enabled());
        assertNotNull(service.schedule().jobs().get(0).nextRunAt());
    }

    @Test
    @DisplayName("an admin's schedule survives a restart: saved rows win over the defaults")
    void savedRowsWin() {
        rows.put("bracSync", ScheduledJobSetting.builder().jobKey("bracSync").cron("0 40 9-17 * * *").enabled(false).build());
        JobScheduleServiceImpl service = service(true);
        service.start();

        assertEquals("0 40 9-17 * * *", rows.get("bracSync").getCron());
        verify(taskScheduler, times(ScheduledJob.values().length - 1)).schedule(any(Runnable.class), any(Trigger.class));
        var brac = service.schedule().jobs().stream().filter(j -> j.key().equals("bracSync")).findFirst().orElseThrow();
        assertFalse(brac.enabled());
        assertNull(brac.nextRunAt(), "a job that is off has no next run");
    }

    @Test
    @DisplayName("switching a job off cancels it; a new schedule re-arms it at once")
    void switchOffAndReschedule() {
        JobScheduleServiceImpl service = service(true);
        service.start();
        int before = futures.size();

        service.update("bracSync", false, null, "admin@x");
        ScheduledFuture<?> bracFuture = futures.get(ScheduledJob.BRAC_SYNC.ordinal());
        verify(bracFuture).cancel(false);
        assertEquals(before, futures.size(), "nothing new is armed for a job that is off");
        assertEquals("admin@x", rows.get("bracSync").getUpdatedBy());

        service.update("bracSync", true, "0 40 9-17 * * *", "admin@x");
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler, times(before + 1)).schedule(any(Runnable.class), trigger.capture());
        assertEquals("0 40 9-17 * * *", ((CronTrigger) trigger.getValue()).getExpression());
        verify(runner, times(2)).clearFailures(ScheduledJob.BRAC_SYNC);
    }

    @Test
    @DisplayName("each firing is recorded before the job runs, so 'last fire' holds even for a skipped run")
    void firingIsRecorded() {
        JobScheduleServiceImpl service = service(true);
        service.start();
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, times(ScheduledJob.values().length)).schedule(tasks.capture(), any(Trigger.class));

        tasks.getAllValues().get(ScheduledJob.BRAC_SYNC.ordinal()).run();

        InOrder order = inOrder(repo, runner);
        order.verify(repo).markFired(eq("bracSync"), any(Instant.class));
        order.verify(runner).run(ScheduledJob.BRAC_SYNC);
    }

    @Test
    @DisplayName("back to default restores the shipped schedule and switches the job on")
    void reset() {
        JobScheduleServiceImpl service = service(true);
        service.start();
        service.update("bracSync", false, "0 40 9-17 * * *", "admin@x");

        var job = service.reset("bracSync", "admin@x");
        assertEquals("0 10 8-20 * * *", job.cron());
        assertTrue(job.enabled());
    }

    @Test
    @DisplayName("with scheduling off on the server nothing is armed, but settings still save")
    void schedulingOff() {
        JobScheduleServiceImpl service = service(false);
        service.start();
        service.update("bracSync", null, "0 40 9-17 * * *", "admin@x");

        verifyNoInteractions(taskScheduler);
        assertFalse(service.schedule().enabled());
        assertEquals("0 40 9-17 * * *", rows.get("bracSync").getCron());
    }

    @Test
    @DisplayName("a schedule that is not valid, or runs too often for the job, is refused in plain words")
    void rules() {
        var invalid = assertThrows(IllegalArgumentException.class,
                () -> JobScheduleServiceImpl.validate(ScheduledJob.BRAC_SYNC, "every hour", DHAKA));
        assertTrue(invalid.getMessage().startsWith("That is not a valid schedule"));

        var tooOften = assertThrows(IllegalArgumentException.class,
                () -> JobScheduleServiceImpl.validate(ScheduledJob.BRAC_SYNC, "0 */5 * * * *", DHAKA));
        assertEquals("BRAC e-Tender can run at most once every 10 minutes. More often would put too much load on the portal.",
                tooOften.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> JobScheduleServiceImpl.validate(ScheduledJob.EGP_RECONCILE, "0 0 * * * *", DHAKA),
                "the hour-long e-GP re-check cannot run hourly");
        assertDoesNotThrow(() -> JobScheduleServiceImpl.validate(ScheduledJob.EGP_RECONCILE, "0 0 2 * * *", DHAKA));
        assertDoesNotThrow(() -> JobScheduleServiceImpl.validate(ScheduledJob.BRAC_SYNC, "0 10 8-20 * * *", DHAKA));
        assertDoesNotThrow(() -> JobScheduleServiceImpl.validate(ScheduledJob.EGP_DISCOVERY, "0 0/30 8-20 * * *", DHAKA));
    }

    @Test
    @DisplayName("a five-field cron, as most guides write it, gains its seconds instead of being refused")
    void fiveFields() {
        assertEquals("0 10 8-20 * * *", JobScheduleServiceImpl.normalise("  10  8-20 * * * "));
        assertEquals("0 10 8-20 * * *", JobScheduleServiceImpl.normalise("0 10 8-20 * * *"));
    }

    @Test
    @DisplayName("the preview lists the next five runs, or says why it would be refused; nothing is saved")
    void preview() {
        JobScheduleServiceImpl service = service(true);
        var ok = service.preview("bracSync", "0 10 8-20 * * *");
        assertTrue(ok.valid());
        assertEquals(JobScheduleServiceImpl.PREVIEW_RUNS, ok.nextRuns().size());

        var refused = service.preview("bracSync", "0 * * * * *");
        assertFalse(refused.valid());
        assertTrue(refused.message().contains("at most once every 10 minutes"));
        assertTrue(rows.isEmpty(), "a preview never writes");
    }
}
