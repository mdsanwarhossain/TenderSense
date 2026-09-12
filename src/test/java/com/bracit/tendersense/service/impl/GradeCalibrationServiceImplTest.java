package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.MatchGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The grade bands, at the percentages people see on screen. */
class GradeCalibrationServiceImplTest {

    private final GradeCalibrationServiceImpl grades = new GradeCalibrationServiceImpl();
    private final Organisation org = new Organisation();

    private MatchGrade at(double score) {
        return grades.grade(org, score);
    }

    @Test
    @DisplayName("S 80-100%, A 60-79%, B 30-59%, C below 30%")
    void bands() {
        assertEquals(MatchGrade.S, at(1.00));
        assertEquals(MatchGrade.S, at(0.80));
        assertEquals(MatchGrade.A, at(0.79));
        assertEquals(MatchGrade.A, at(0.60));
        assertEquals(MatchGrade.B, at(0.59));
        assertEquals(MatchGrade.B, at(0.30));
        assertEquals(MatchGrade.C, at(0.29));
        assertEquals(MatchGrade.C, at(0.00));
    }

    @Test
    @DisplayName("a score that shows as 80% is S, one that shows as 79% is A")
    void gradedOnTheRoundedPercentage() {
        assertEquals(MatchGrade.S, at(0.796));   // shown as 80%
        assertEquals(MatchGrade.A, at(0.794));   // shown as 79%
        assertEquals(MatchGrade.A, at(0.596));   // shown as 60%
        assertEquals(MatchGrade.B, at(0.296));   // shown as 30%
        assertEquals(MatchGrade.C, at(0.294));   // shown as 29%
    }

    @Test
    @DisplayName("the bands do not move with a company's scores")
    void sameForEveryCompany() {
        var low = grades.calibrate(org, List.of(0.05, 0.10, 0.12, 0.20));
        var high = grades.calibrate(org, List.of(0.60, 0.70, 0.90));
        assertEquals(low, high);
        assertEquals(0.795, low.s());
    }
}
