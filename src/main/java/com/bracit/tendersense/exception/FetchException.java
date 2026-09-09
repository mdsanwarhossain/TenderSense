package com.bracit.tendersense.exception;

/** Thrown when a source portal cannot be read or returns something unusable. */
public class FetchException extends RuntimeException {

    public FetchException(String message) {
        super(message);
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
