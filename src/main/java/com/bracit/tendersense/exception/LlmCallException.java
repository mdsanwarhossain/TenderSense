package com.bracit.tendersense.exception;

/**
 * The model answered, but not usefully for this tender -- a timeout on a very long
 * notice, or a reply that is not the JSON asked for. Counts as one failed attempt.
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
