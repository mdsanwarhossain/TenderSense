package com.bracit.tendersense.controller.admin;

import com.bracit.tendersense.dto.CronPreview;
import com.bracit.tendersense.dto.ScheduleResponse;
import com.bracit.tendersense.dto.admin.AdminRequests;
import com.bracit.tendersense.security.AccountPrincipal;
import com.bracit.tendersense.service.JobScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The Scheduler page: switch scheduled jobs on and off and change when they run, without
 * a restart. Admin only (SecurityConfig: {@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/schedule")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final JobScheduleService jobScheduleService;

    @GetMapping
    public ScheduleResponse schedule() {
        return jobScheduleService.schedule();
    }

    @PatchMapping("/{key}")
    public ScheduleResponse.Job update(@AuthenticationPrincipal AccountPrincipal me,
                                       @PathVariable String key,
                                       @RequestBody AdminRequests.JobUpdate request) {
        if (request.enabled() == null && request.cron() == null) {
            throw new IllegalArgumentException("Nothing to change.");
        }
        return jobScheduleService.update(key, request.enabled(), request.cron(), me.getUsername());
    }

    @PostMapping("/{key}/reset")
    public ScheduleResponse.Job reset(@AuthenticationPrincipal AccountPrincipal me, @PathVariable String key) {
        return jobScheduleService.reset(key, me.getUsername());
    }

    /** For the editor's live preview: nothing is saved. */
    @GetMapping("/{key}/preview")
    public CronPreview preview(@PathVariable String key, @RequestParam String cron) {
        return jobScheduleService.preview(key, cron);
    }
}
