package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.Tender;

import java.util.List;

public interface ScoringService {

    /** Scores, grades, eligibility-checks and summarises a batch. Returns rows written. */
    int scoreAll(List<Tender> tenders);

    /** Re-scores everything: used after a profile edit or model/parser change. */
    int rescoreEverything();

    /**
     * Recalibrates thresholds against the full stored distribution and rewrites every
     * grade. Grading is comparative, so it can only be correct once all scores exist --
     * calibrating per batch would grade identical tenders differently depending on
     * which batch they landed in.
     */
    void recalibrateGrades();
}
