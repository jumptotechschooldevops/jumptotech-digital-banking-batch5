package com.jumptotech.banking.account.dto;

import com.jumptotech.banking.account.AccountStatus;
import com.jumptotech.banking.account.AccountType;

import java.util.UUID;

public record AccountDto(
        UUID id,
        String maskedAccountNumber,
        AccountType accountType,
        long balanceCents,
        String currency,
        AccountStatus status
) {
}
