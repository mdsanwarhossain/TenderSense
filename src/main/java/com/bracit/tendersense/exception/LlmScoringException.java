package com.bracit.tendersense.exception;

/**
 * No valid verdict for one tender after every attempt. The run records this tender as
 * FAILED and carries on with the next one.
 */
public class LlmScoringException extends RuntimeException {

    public LlmScoringException(String message) {
        super(message);
    }

    public LlmScoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
