package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

/** Deliberately generic — must never reveal whether the email or the password was wrong. */
public class InvalidCredentialsException extends BankingException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
    }
}
