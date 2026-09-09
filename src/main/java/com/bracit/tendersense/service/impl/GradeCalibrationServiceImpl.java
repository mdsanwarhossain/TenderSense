package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.service.GradeCalibrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Percentile calibration against the live corpus.
 *
 * <p>A shortlist is inherently comparative -- the BD team wants the best tenders
 * available today, not every tender above some absolute cosine value that would
 * drift as the corpus or the embedding model changes. So S is the top slice of the
 * observed distribution rather than a fixed number.
 *
 * <p>Once the labeled set arrives, {@link #calibrate} should instead choose the cut
 * points that best separate labeled-relevant from labeled-irrelevant, and report
 * that separation as the basis.
 */
@Service
@Slf4j
public class GradeCalibrationServiceImpl implements GradeCalibrationService {

    private static final double S_PERCENTILE = 0.98;
    private static final double A_PERCENTILE = 0.90;
    private static final double B_PERCENTILE = 0.70;

    /** Used before any calibration has run, so grading never throws. */
    private static final Thresholds FALLBACK =
            new Thresholds(0.55, 0.45, 0.35, "uncalibrated defaults");

    /**
     * Per organisation. A construction firm's score distribution is not an IT firm's, so
     * one set of thresholds across tenants would grade one of them against the other's
     * corpus.
     */
    private final java.util.Map<Long, Thresholds> byOrganisation =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Thresholds calibrate(Organisation organisation, List<Double> scores) {
        List<Double> sorted = new ArrayList<>(scores.stream().filter(s -> s != null && s > 0).toList());
        Thresholds existing = byOrganisation.getOrDefault(organisation.getId(), FALLBACK);
        if (sorted.size() < 20) {
            log.warn("{}: only {} usable scores - keeping {} thresholds",
                    organisation.getSlug(), sorted.size(), existing.basis());
            return existing;
        }
        sorted.sort(Double::compareTo);

        Thresholds t = new Thresholds(
                percentile(sorted, S_PERCENTILE),
                percentile(sorted, A_PERCENTILE),
                percentile(sorted, B_PERCENTILE),
                "percentiles p98/p90/p70 over %d scored tenders".formatted(sorted.size()));

        byOrganisation.put(organisation.getId(), t);
        log.info("grade thresholds for {}: S>={} A>={} B>={} ({})",
                organisation.getSlug(), fmt(t.s()), fmt(t.a()), fmt(t.b()), t.basis());
        return t;
    }

    @Override
    public Thresholds thresholds(Organisation organisation) {
        return byOrganisation.getOrDefault(organisation.getId(), FALLBACK);
    }

    @Override
    public MatchGrade grade(Organisation organisation, double score) {
        Thresholds t = thresholds(organisation);
        if (score >= t.s()) {
            return MatchGrade.S;
        }
        if (score >= t.a()) {
            return MatchGrade.A;
        }
        if (score >= t.b()) {
            return MatchGrade.B;
        }
        return MatchGrade.C;
    }

    private static double percentile(List<Double> sortedAscending, double p) {
        int idx = (int) Math.floor(p * (sortedAscending.size() - 1));
        return sortedAscending.get(Math.max(0, Math.min(idx, sortedAscending.size() - 1)));
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }
}
