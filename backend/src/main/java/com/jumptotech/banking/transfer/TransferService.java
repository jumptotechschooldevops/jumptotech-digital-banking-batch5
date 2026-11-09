package com.jumptotech.banking.transfer;

import com.jumptotech.banking.transfer.dto.CreateTransferRequest;
import com.jumptotech.banking.transfer.dto.TransferDetailDto;
import com.jumptotech.banking.transfer.dto.TransferExecutionResult;

import java.util.UUID;

public interface TransferService {

    /**
     * Executes (or, for a replayed request, returns the previously recorded
     * outcome of) a transfer. See docs/architecture.md "The transfer
     * invariant" for the full list of checks this performs.
     */
    TransferExecutionResult executeTransfer(UUID userId, CreateTransferRequest request,
                                             String idempotencyKeyHeader, String ipAddress);

    TransferDetailDto getTransfer(UUID userId, UUID transferId);
}
