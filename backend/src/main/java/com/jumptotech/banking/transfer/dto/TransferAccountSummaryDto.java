package com.jumptotech.banking.transfer.dto;

import java.util.UUID;

public record TransferAccountSummaryDto(
        UUID id,
        String maskedAccountNumber,
        long balanceCents
) {
}
