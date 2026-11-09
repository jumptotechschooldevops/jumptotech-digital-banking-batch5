package com.jumptotech.banking.account;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "balance_cents", nullable = false)
    private long balanceCents = 0L;

    @Column(nullable = false)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Account(UUID ownerUserId, String accountNumber, AccountType accountType) {
        this.ownerUserId = ownerUserId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balanceCents = 0L;
        this.currency = "USD";
        this.status = AccountStatus.ACTIVE;
    }

    /** Throws if the debit would take the balance negative. Caller is expected to hold a row lock. */
    public void debit(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (balanceCents < amountCents) {
            throw new IllegalStateException("Insufficient funds for debit");
        }
        this.balanceCents -= amountCents;
    }

    public void credit(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balanceCents += amountCents;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.ownerUserId.equals(userId);
    }

    public String maskedAccountNumber() {
        int len = accountNumber.length();
        String last4 = accountNumber.substring(Math.max(0, len - 4));
        return "****" + last4;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
