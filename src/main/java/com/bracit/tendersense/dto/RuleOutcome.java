package com.bracit.tendersense.dto;

/**
 * One rule's verdict on one tender.
 *
 * <p>{@link Result#UNKNOWN} is a first-class outcome, not a failure to try: about half
 * of e-GP tenders state eligibility as "As per TDS", and asserting a pass or a fail on
 * text that says nothing would be a fabrication. UNKNOWN surfaces as
 * "verify manually" rather than being silently treated as a pass.
 */
public record RuleOutcome(String ruleCode,
                          Result result,
                          String requirement,
                          String actual,
                          String message) {

    public enum Result {
        PASS,
        FAIL,
        UNKNOWN,
        /** The rule does not apply to this tender at all. */
        NOT_APPLICABLE
    }

    public static RuleOutcome pass(String code, String requirement, String actual, String msg) {
        return new RuleOutcome(code, Result.PASS, requirement, actual, msg);
    }

    public static RuleOutcome fail(String code, String requirement, String actual, String msg) {
        return new RuleOutcome(code, Result.FAIL, requirement, actual, msg);
    }

    public static RuleOutcome unknown(String code, String msg) {
        return new RuleOutcome(code, Result.UNKNOWN, null, null, msg);
    }

    public static RuleOutcome notApplicable(String code) {
        return new RuleOutcome(code, Result.NOT_APPLICABLE, null, null, null);
    }
}
