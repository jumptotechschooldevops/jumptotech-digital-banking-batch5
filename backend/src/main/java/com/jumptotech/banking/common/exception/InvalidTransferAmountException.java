package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransferAmountException extends BankingException {
    public InvalidTransferAmountException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_AMOUNT", "The transfer amount must be a positive number of cents.");
    }
}
