package com.bracit.tendersense.entity.enums;

/** Lightweight projection so the shortlist does not load full verdict graphs. */
public record EligibilityVerdictView(EligibilityStatus status, int blockingGapCount) {
}
