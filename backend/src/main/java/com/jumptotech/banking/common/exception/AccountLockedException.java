package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends BankingException {
    public AccountLockedException() {
        super(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "This account is temporarily locked due to too many failed login attempts.");
    }
}
