package com.bracit.tendersense.exception;

/**
 * The model server cannot be reached, or the model is not installed. Not the tender's
 * fault: the batch stops and the same tenders are tried again on the next tick.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
