package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;

import java.util.List;
import java.util.Map;

/**
 * Two implementations sit behind this: semantic embeddings and a keyword baseline.
 *
 * <p>The baseline is a real implementation, not a strawman. The BRD's headline
 * success criterion is a comparison, and a comparison against a deliberately weak
 * opponent proves nothing.
 *
 * <p>Scoring is expressed in bulk because the keyword matcher answers for a whole
 * batch in a single SQL statement; per-tender scoring would be thousands of queries.
 */
public interface MatchingService {

    MatcherType type();

    /** Identifies the scoring model, so stored scores can be invalidated on change. */
    String modelVersion();

    Map<Long, ScoredMatch> scoreAll(Organisation organisation, List<Tender> tenders);

    default ScoredMatch score(Organisation organisation, Tender tender) {
        return scoreAll(organisation, List.of(tender))
                .getOrDefault(tender.getId(), ScoredMatch.zero());
    }
}
