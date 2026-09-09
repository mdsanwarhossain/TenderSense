package com.bracit.tendersense.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the two ways a scheduler silently does the wrong thing: firing at the wrong
 * hour because nobody pinned a timezone, and firing at all during a demo.
 */
class PipelineSchedulerTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    @Nested
    @DisplayName("cron expressions")
    class Crons {

        private final List<String> configured = List.of(
                "0 0/30 8-20 * * *",   // egpDiscovery
                "0 0 2 * * *",         // egpReconcile
                "0 15 */6 * * *",      // worldBankSync
                "0 0 8 * * *");        // morningDigest

        @Test
        @DisplayName("all parse as Spring 6-field expressions")
        void allParse() {
            configured.forEach(cron -> assertDoesNotThrow(
                    () -> CronExpression.parse(cron), "unparseable cron: " + cron));
        }

        @Test
        @DisplayName("the digest fires at 08:00 Dhaka, not 08:00 server-local")
        void digestFiresAtDhakaEight() {
            // Anchored just after midnight Dhaka so the next firing is the same day's 08:00.
            ZonedDateTime from = ZonedDateTime.of(
                    LocalDateTime.of(2026, 9, 10, 0, 5), DHAKA);
            ZonedDateTime next = CronExpression.parse("0 0 8 * * *").next(from);

            assertNotNull(next);
            assertEquals(8, next.withZoneSameInstant(DHAKA).getHour(),
                    "digest must land at 08:00 Dhaka");
            assertEquals(10, next.withZoneSameInstant(DHAKA).getDayOfMonth());
        }

        @Test
        @DisplayName("discovery runs on the half hour inside business hours only")
        void discoveryWindow() {
            CronExpression cron = CronExpression.parse("0 0/30 8-20 * * *");

            // 07:00 -> first firing of the day is 08:00, not 07:30.
            ZonedDateTime early = cron.next(ZonedDateTime.of(
                    LocalDateTime.of(2026, 9, 10, 7, 0), DHAKA));
            assertEquals(8, early.getHour());
            assertEquals(0, early.getMinute());

            // 20:30 is the last slot; the next is tomorrow morning.
            ZonedDateTime afterLast = cron.next(ZonedDateTime.of(
                    LocalDateTime.of(2026, 9, 10, 20, 31), DHAKA));
            assertEquals(11, afterLast.getDayOfMonth());
            assertEquals(8, afterLast.getHour());
        }

        @Test
        @DisplayName("reconcile runs overnight, well clear of the discovery window")
        void reconcileIsOvernight() {
            ZonedDateTime next = CronExpression.parse("0 0 2 * * *").next(
                    ZonedDateTime.of(LocalDateTime.of(2026, 9, 10, 23, 0), DHAKA));
            assertEquals(2, next.getHour());
            // Discovery starts at 08:00; a ~1 hour reconcile at 02:00 finishes long before.
            assertTrue(next.getHour() < 8, "reconcile must not collide with discovery");
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "tendersense.source.mode=cached",
            "tendersense.schedule.enabled=false"
    })
    @DisplayName("demo profile")
    class Disabled {

        @Autowired
        private ApplicationContext context;
        @Autowired
        private Environment environment;

        @Test
        @DisplayName("no scheduler bean exists when scheduling is switched off")
        void schedulerAbsent() {
            assertFalse(environment.getProperty("tendersense.schedule.enabled", Boolean.class, true));
            assertEquals(0, context.getBeanNamesForType(PipelineScheduler.class).length,
                    "the demo profile must leave no scheduled job able to fire");
        }
    }

    @Nested
    @SpringBootTest(properties = "tendersense.source.mode=cached")
    @DisplayName("default configuration")
    class Enabled {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("scheduler is wired when scheduling is left on")
        void schedulerPresent() {
            assertEquals(1, context.getBeanNamesForType(PipelineScheduler.class).length);
        }
    }
}
