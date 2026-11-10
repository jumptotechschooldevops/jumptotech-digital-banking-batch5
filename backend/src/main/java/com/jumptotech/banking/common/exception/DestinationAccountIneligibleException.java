package com.jumptotech.banking.common.exception;

import org.springframework.http.HttpStatus;

public class DestinationAccountIneligibleException extends BankingException {
    public DestinationAccountIneligibleException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "DESTINATION_ACCOUNT_INELIGIBLE", message);
    }
}
