package com.bracit.tendersense.entity.enums;

/**
 * Both matchers persist results so the benchmark compares stored scores rather
 * than re-running either system on stage.
 */
public enum MatcherType {
    EMBEDDING,
    KEYWORD
}
