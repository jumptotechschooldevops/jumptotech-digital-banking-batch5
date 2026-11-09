package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BankingException {
    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}
