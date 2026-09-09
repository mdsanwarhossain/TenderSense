package com.bracit.tendersense.entity.enums;

/**
 * Act-on-it grades rather than percentages, per the BRD. Thresholds are not
 * hardcoded here: {@code GradeCalibrationService} derives them from the labeled
 * dev set so the cut points can be justified rather than asserted.
 */
public enum MatchGrade {
    S, A, B, C
}
