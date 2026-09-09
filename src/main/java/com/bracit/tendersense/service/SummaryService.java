package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;

/**
 * Writes the human-readable "why this matched" line.
 *
 * <p>Used for readability only: no pass/fail decision anywhere in the system reads
 * this text. That separation is what makes it safe to swap a template writer for an
 * LLM without changing what the system decides.
 */
public interface SummaryService {

    String summarise(Organisation organisation, Tender tender, ScoredMatch match,
                     EligibilityVerdict verdict);
}
