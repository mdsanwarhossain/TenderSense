package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.MatchGrade;

import java.util.List;

/**
 * Turns the match score into the S/A/B/C grades the BRD asks for.
 *
 * <p>The bands are fixed percentages chosen by the product owner, so a grade means the
 * same thing for every company and on every screen -- see GradeCalibrationServiceImpl.
 */
public interface GradeCalibrationService {

    /** Recomputes thresholds from an observed score distribution. */
    Thresholds calibrate(Organisation organisation, List<Double> scores);

    Thresholds thresholds(Organisation organisation);

    MatchGrade grade(Organisation organisation, double score);

    record Thresholds(double s, double a, double b, String basis) {
    }
}
