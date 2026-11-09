package com.jumptotech.banking.transfer.dto;

import com.jumptotech.banking.transfer.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferConfirmationDto(
        UUID transferId,
        TransferStatus status,
        long amountCents,
        TransferAccountSummaryDto sourceAccount,
        TransferAccountSummaryDto destinationAccount,
        Instant completedAt
) {
}
