package com.bracit.tendersense.entity.enums;

/**
 * Where a tender stands in the second-stage LLM review. {@code null} on a row means it
 * has never been selected for review -- it was never on a company's shortlist page one.
 */
public enum LlmReviewStatus {
    /** Selected and queued; the model has not answered yet. */
    PENDING,
    /** Scored against the current tender text, profile, model and prompt. */
    SCORED,
    /** The model was asked and no valid verdict came back. */
    FAILED,
    /**
     * Scored once, but an input has changed since -- the profile was edited, the tender
     * was revised, or the model or prompt changed. The old verdict is kept for comparison
     * and is not shown as current.
     */
    STALE
}
