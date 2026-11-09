package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends BankingException {
    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
    }
}
