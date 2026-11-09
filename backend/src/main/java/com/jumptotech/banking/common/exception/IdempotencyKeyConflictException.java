package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends BankingException {
    public IdempotencyKeyConflictException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                "This idempotency key was already used with a different request.");
    }
}
