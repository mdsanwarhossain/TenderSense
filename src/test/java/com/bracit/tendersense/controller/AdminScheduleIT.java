package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.ScheduledJobSetting;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.repository.ScheduledJobSettingRepository;
import com.bracit.tendersense.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The Scheduler page's API, end to end. Shares the development database, so the one row
 * it changes is put back exactly as it was.
 */
@SpringBootTest(properties = "tendersense.source.mode=cached")
@AutoConfigureMockMvc
class AdminScheduleIT {

    private static final String JOB = "bracSync";

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ScheduledJobSettingRepository settings;

    private RequestPostProcessor admin;
    private ScheduledJobSetting before;

    @BeforeEach
    void setUp() {
        admin = TestAuth.admin(accountRepository);
        ScheduledJobSetting row = settings.findById(JOB).orElseThrow();
        before = ScheduledJobSetting.builder().jobKey(row.getJobKey()).cron(row.getCron()).enabled(row.isEnabled())
                .updatedAt(row.getUpdatedAt()).updatedBy(row.getUpdatedBy()).lastFiredAt(row.getLastFiredAt()).build();
    }

    @AfterEach
    void restore() {
        settings.save(before);
    }

    private String body(String json) {
        return json;
    }

    @Test
    @DisplayName("lists the seven jobs with their schedule, default and switch")
    void lists() throws Exception {
        mockMvc.perform(get("/api/admin/schedule").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(7))
                .andExpect(jsonPath("$.jobs[?(@.key == 'bracSync')].defaultCron").value("0 10 8-20 * * *"));
    }

    @Test
    @DisplayName("an admin switches a job off and moves it; it is recorded who did")
    void switchOffAndMove() throws Exception {
        mockMvc.perform(patch("/api/admin/schedule/{key}", JOB).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"enabled\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.nextRunAt").doesNotExist());

        mockMvc.perform(patch("/api/admin/schedule/{key}", JOB).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"enabled\":true,\"cron\":\"40 9-17 * * *\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cron").value("0 40 9-17 * * *"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updatedBy").value("admin@tendersense.local"));

        mockMvc.perform(post("/api/admin/schedule/{key}/reset", JOB).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cron").value("0 10 8-20 * * *"));
    }

    @Test
    @DisplayName("a schedule that runs too often is refused with a message the page shows as-is")
    void refused() throws Exception {
        mockMvc.perform(patch("/api/admin/schedule/{key}", JOB).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"cron\":\"0 * * * * *\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("at most once every 10 minutes")));
        mockMvc.perform(patch("/api/admin/schedule/{key}", "noSuchJob").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"enabled\":false}")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the preview shows the next five runs without saving anything")
    void preview() throws Exception {
        mockMvc.perform(get("/api/admin/schedule/{key}/preview", JOB).param("cron", "0 0 9 * * MON-FRI").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.nextRuns.length()").value(5));
        mockMvc.perform(get("/api/admin/schedule").with(admin))
                .andExpect(jsonPath("$.jobs[?(@.key == 'bracSync')].cron").value(before.getCron()));
    }
}
