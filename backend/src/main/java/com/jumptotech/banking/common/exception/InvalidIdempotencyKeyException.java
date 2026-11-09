package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidIdempotencyKeyException extends BankingException {
    public InvalidIdempotencyKeyException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "The Idempotency-Key header is required and must be a valid UUID.");
    }
}
