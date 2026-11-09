package com.jumptotech.banking.transaction.dto;

import com.jumptotech.banking.ledger.EntryType;
import com.jumptotech.banking.transfer.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransactionDto(
        UUID id,
        UUID transferId,
        Instant occurredAt,
        EntryType type,
        long amountCents,
        long balanceAfterCents,
        TransferStatus status,
        String counterpartyMaskedAccountNumber,
        String description
) {
}
