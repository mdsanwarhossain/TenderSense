package com.bracit.tendersense.scheduler;

/**
 * What runs when a scheduled job fires. Absent when {@code tendersense.schedule.enabled}
 * is false (the demo profile), in which case nothing is armed at all.
 */
public interface ScheduledJobRunner {

    /** One firing of the job, behind its failure guard. */
    void run(ScheduledJob job);

    /** True once the job has failed several times in a row and is sitting out its runs. */
    boolean paused(ScheduledJob job);

    /** Lets a paused job try again: an admin changing it counts as someone looking at it. */
    void clearFailures(ScheduledJob job);
}
