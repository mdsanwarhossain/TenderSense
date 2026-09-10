package com.bracit.tendersense.exception;

/**
 * The model cannot be reached at all -- the server is down, or the model is not pulled.
 * Distinct from a bad answer for one tender: this aborts the whole review run, because
 * every remaining call would fail the same way.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
