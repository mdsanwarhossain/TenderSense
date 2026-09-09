package com.bracit.tendersense.dto;

import java.util.List;

/**
 * Semantic vs keyword on the held-out set. The keyword baseline is a real
 * implementation, not a strawman -- the comparison is meaningless otherwise.
 */
public record BenchmarkResponse(
        int heldOutCount,
        int k,
        MatcherScore semantic,
        MatcherScore keyword,
        List<Comparison> comparisons,
        String caveat) {

    public record MatcherScore(String matcher, int hits, int total, double precisionAtK) {
    }

    public record Comparison(Long tenderId, String title, boolean labelledRelevant,
                             Integer semanticRank, Integer keywordRank) {
    }
}
