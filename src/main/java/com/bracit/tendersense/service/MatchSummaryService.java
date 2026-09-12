package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.MatchSummaryResponse;
import com.bracit.tendersense.entity.Organisation;

/**
 * The written comparison between a tender and a company, for the detail page.
 *
 * <p>Never blocks the request on the model: it answers from the day's cache, and otherwise
 * says it is writing one and hands the work to a background thread. The page polls.
 */
public interface MatchSummaryService {

    MatchSummaryResponse forTender(Organisation organisation, Long tenderId);
}
