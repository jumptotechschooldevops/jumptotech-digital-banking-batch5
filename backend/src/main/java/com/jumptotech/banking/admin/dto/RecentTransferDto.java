package com.jumptotech.banking.admin.dto;

import com.jumptotech.banking.transfer.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record RecentTransferDto(
        UUID transferId,
        long amountCents,
        TransferStatus status,
        Instant completedAt
) {
}
