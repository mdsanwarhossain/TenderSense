package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.service.GradeCalibrationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fixed grade bands on the match percentage, set by the product owner and the same for
 * every company: S 80-100%, A 60-79%, B 30-59%, C below 30%.
 *
 * <p>The bands sit on the percentage people see, which is rounded: a score of 0.796 shows
 * as "80%", so it is graded S. Hence the half-point cut-offs below.
 *
 * <p>This replaced percentile calibration (S = a company's top 2%), under which a grade
 * was relative -- a 32% match could be C for one company and B for another, and never
 * agreed with the colour of its ring.
 */
@Service
public class GradeCalibrationServiceImpl implements GradeCalibrationService {

    static final Thresholds BANDS =
            new Thresholds(0.795, 0.595, 0.295, "fixed bands: S >= 80%, A >= 60%, B >= 30%");

    /** The observed scores no longer move the cut-offs; kept so callers recalibrate as before. */
    @Override
    public Thresholds calibrate(Organisation organisation, List<Double> scores) {
        return BANDS;
    }

    @Override
    public Thresholds thresholds(Organisation organisation) {
        return BANDS;
    }

    @Override
    public MatchGrade grade(Organisation organisation, double score) {
        if (score >= BANDS.s()) {
            return MatchGrade.S;
        }
        if (score >= BANDS.a()) {
            return MatchGrade.A;
        }
        if (score >= BANDS.b()) {
            return MatchGrade.B;
        }
        return MatchGrade.C;
    }
}
