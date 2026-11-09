package com.jumptotech.banking.transfer.dto;

/** Lets the controller return 201 for a newly executed transfer and 200 for a replayed idempotent request. */
public record TransferExecutionResult(TransferConfirmationDto confirmation, boolean replayed) {
}
