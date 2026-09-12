package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.CronPreview;
import com.bracit.tendersense.dto.ScheduleResponse;

/**
 * The schedules of the jobs that run on a clock: read, switch on and off, move. Changes
 * are saved and take effect at once -- no restart.
 */
public interface JobScheduleService {

    ScheduleResponse schedule();

    /**
     * @param enabled null to leave as it is
     * @param cron    null to leave as it is; refused if invalid or it would run too often
     * @param actor   the admin's email, recorded against the change
     */
    ScheduleResponse.Job update(String key, Boolean enabled, String cron, String actor);

    /** Back to the schedule the application ships with. */
    ScheduleResponse.Job reset(String key, String actor);

    /** The next runs a schedule would give this job, or why it would be refused. */
    CronPreview preview(String key, String cron);
}
