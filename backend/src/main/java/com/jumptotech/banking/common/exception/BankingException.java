package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for every domain exception in this application. Carries the
 * HTTP status and a stable machine-readable error code so
 * {@code GlobalExceptionHandler} never has to guess how to map a failure.
 */
public abstract class BankingException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected BankingException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
