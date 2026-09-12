package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.MatchComparison;
import com.bracit.tendersense.dto.MatchEvidenceResponse;
import com.bracit.tendersense.dto.TenderEnrichment;
import com.bracit.tendersense.entity.CapabilityProfile;

import java.util.List;
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

    /**
     * Compares one tender with one company's profile, for the tender detail page.
     *
     * @param evidence the overlaps the matcher already found, so the model grounds its
     *                 points on real text rather than inventing them
     * @throws com.bracit.tendersense.exception.LlmUnavailableException the server is down
     *         or the model is not installed
     * @throws com.bracit.tendersense.exception.LlmCallException this call failed
     */
    MatchComparison compare(Tender tender, CapabilityProfile profile,
                            List<MatchEvidenceResponse.EvidencePair> evidence, int attempt);

    /** Stored with each result, so a model change can re-process what it produced. */
    String model();

    /** The smaller model behind {@link #compare}: stored with each cached comparison. */
    String summaryModel();
}
