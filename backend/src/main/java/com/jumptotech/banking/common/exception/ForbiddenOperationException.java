package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends BankingException {
    public ForbiddenOperationException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}
