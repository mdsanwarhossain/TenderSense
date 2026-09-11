package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.TenderEnrichment;
import com.bracit.tendersense.entity.Tender;

/**
 * Reads one tender with the local model. An interface so tests run without Ollama and
 * another model server can be dropped in.
 */
public interface LlmClient {

    /**
     * The model's raw reading of the tender -- not yet validated.
     *
     * @param attempt 0 for the first try. A retry must not repeat the identical request:
     *                at temperature 0 it would return the identical bad answer.
     *
     * @throws com.bracit.tendersense.exception.LlmUnavailableException the server is down
     *         or the model is not installed; nothing is wrong with this tender
     * @throws com.bracit.tendersense.exception.LlmCallException this call failed
     */
    TenderEnrichment enrich(Tender tender, int attempt);

    /** Stored with each result, so a model change can re-process what it produced. */
    String model();
}
