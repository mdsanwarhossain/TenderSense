package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.Organisation;

/**
 * The second-stage review: a local LLM reads each tender on a company's shortlist page one
 * and records a 0-100 verdict with a reason, beside the existing embedding score.
 *
 * <p>Runs on its own background thread and its own queue, not under {@code PipelineLock}.
 * On CPU a run takes minutes; holding the pipeline lock that long would turn every
 * collection run in the meantime into a SKIPPED. That separation is safe only because
 * this job writes nothing but the {@code llm_*} columns.
 */
public interface LlmReviewService {

    /**
     * Queues a review. Returns immediately. A no-op returning false when the feature is
     * switched off or this company is already waiting in the queue.
     */
    boolean request(Organisation organisation);

    /** Queues every active company. */
    void requestAll();

    /** Runs one review on the calling thread. The worker calls this; so do tests. */
    Outcome review(Organisation organisation);

    /**
     * @param selected  page-one rows considered
     * @param upToDate  already scored against the current inputs -- no model call made
     * @param scored    new verdicts written
     * @param failed    asked, but no valid verdict came back
     * @param aborted   why the run stopped early, or null if it ran to the end
     */
    record Outcome(int selected, int upToDate, int scored, int failed, String aborted) {
        public static Outcome skipped(String why) {
            return new Outcome(0, 0, 0, 0, why);
        }
    }
}
