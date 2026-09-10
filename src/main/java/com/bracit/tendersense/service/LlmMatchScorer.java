package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.LlmMatchVerdict;

/**
 * Asks a language model to score one tender against one company.
 *
 * <p>Prompt construction lives in {@code LlmPromptBuilder}; this interface only executes.
 * Kept as an interface so tests can stub the model and another provider can be swapped in
 * without touching the review job.
 */
public interface LlmMatchScorer {

    /** Model identity, part of the review fingerprint: a change re-reviews everything. */
    String modelVersion();

    /**
     * @param systemPrompt instructions, rubric and company profile -- identical for every
     *                     tender of one company, so the server can reuse its cached prefix
     * @param tenderPrompt the tender being judged
     * @throws com.bracit.tendersense.exception.LlmUnavailableException the model is unreachable
     * @throws com.bracit.tendersense.exception.LlmScoringException    no valid verdict after retries
     */
    LlmMatchVerdict score(String systemPrompt, String tenderPrompt);
}
