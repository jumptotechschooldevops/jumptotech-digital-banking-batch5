package com.jumptotech.banking.transaction;

import com.jumptotech.banking.ledger.EntryType;

import java.time.Instant;

public record TransactionFilter(EntryType type, Instant from, Instant to) {
}
