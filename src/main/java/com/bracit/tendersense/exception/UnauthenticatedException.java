package com.bracit.tendersense.exception;

/** No usable session on a request that needs one. Rendered as 401. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
