package com.bracit.tendersense.entity.enums;

/**
 * Act-on-it grades rather than percentages, per the BRD. The bands -- S 80-100%,
 * A 60-79%, B 30-59%, C below 30% -- live in {@code GradeCalibrationServiceImpl}.
 */
public enum MatchGrade {
    S, A, B, C
}
