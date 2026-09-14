package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;

import java.util.List;

public interface ScoringService {

    /** Scores, grades, eligibility-checks and summarises a batch. Returns rows written. */
    int scoreAll(Organisation organisation, List<Tender> tenders);

    /** Re-scores everything: used after a profile edit or model/parser change. */
    int rescoreEverything(Organisation organisation);

    /** Re-scores every active organisation. */
    int rescoreAllOrganisations();

    /**
     * Recalibrates thresholds against the full stored distribution and rewrites every
     * grade. Grading is comparative, so it can only be correct once all scores exist --
     * calibrating per batch would grade identical tenders differently depending on
     * which batch they landed in.
     */
    void recalibrateGrades(Organisation organisation);
}
