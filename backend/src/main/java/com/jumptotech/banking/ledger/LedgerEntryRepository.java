package com.jumptotech.banking.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransferId(UUID transferId);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    Page<LedgerEntry> findByAccountIdAndEntryTypeOrderByCreatedAtDesc(UUID accountId, EntryType entryType, Pageable pageable);

    Page<LedgerEntry> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID accountId, Instant from, Instant to, Pageable pageable);

    Page<LedgerEntry> findByAccountIdAndEntryTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID accountId, EntryType entryType, Instant from, Instant to, Pageable pageable);
}
