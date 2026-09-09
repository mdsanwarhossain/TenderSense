package com.bracit.tendersense.dto;

import java.util.List;

/**
 * A matcher's output for one tender: a normalised 0..1 score plus the evidence
 * behind it. Evidence is not decoration -- it is what turns "0.62" into something
 * a bid manager can check.
 */
public record ScoredMatch(double score, List<Evidence> evidence) {

    public record Evidence(String profileText, String tenderText, double similarity) {
    }

    public static ScoredMatch zero() {
        return new ScoredMatch(0d, List.of());
    }
}
