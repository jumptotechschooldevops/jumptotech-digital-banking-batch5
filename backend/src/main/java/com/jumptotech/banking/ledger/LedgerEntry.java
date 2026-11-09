package com.jumptotech.banking.ledger;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "balance_after_cents", nullable = false)
    private long balanceAfterCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry(UUID transferId, UUID accountId, EntryType entryType, long amountCents, long balanceAfterCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be positive");
        }
        this.transferId = transferId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
