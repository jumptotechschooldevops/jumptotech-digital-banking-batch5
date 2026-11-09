package com.jumptotech.banking.transfer.dto;

import com.jumptotech.banking.transfer.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferDetailDto(
        UUID transferId,
        TransferStatus status,
        long amountCents,
        String description,
        TransferAccountSummaryDto sourceAccount,
        TransferAccountSummaryDto destinationAccount,
        Instant createdAt,
        Instant completedAt
) {
}
