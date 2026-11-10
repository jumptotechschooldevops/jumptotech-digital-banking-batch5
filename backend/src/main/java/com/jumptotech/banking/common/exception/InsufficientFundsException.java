package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS",
                "The source account does not have enough available balance.");
    }
}
