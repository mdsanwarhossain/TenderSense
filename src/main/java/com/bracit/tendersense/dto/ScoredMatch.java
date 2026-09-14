package com.bracit.tendersense.dto;

import java.util.List;

/**
 * A matcher's output for one tender: a normalised 0..1 score plus the evidence
 * behind it. Evidence is not decoration -- it is what turns "0.62" into something
 * a bid manager can check.
 */
public record ScoredMatch(double score,
                          List<Evidence> evidence,
                          Exclusion exclusion) {

    public record Evidence(String profileText, String tenderText, double similarity) {
    }

    /**
     * The "not our work" statement this tender resembled, when that resemblance was
     * strong enough to pull the score down. Null when nothing fired.
     */
    public record Exclusion(String text, double similarity, double penalty) {
    }

    public ScoredMatch(double score, List<Evidence> evidence) {
        this(score, evidence, null);
    }

    public static ScoredMatch zero() {
        return new ScoredMatch(0d, List.of(), null);
    }
}
