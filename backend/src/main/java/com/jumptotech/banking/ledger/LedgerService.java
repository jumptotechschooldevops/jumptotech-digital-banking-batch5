package com.jumptotech.banking.ledger;

import java.util.UUID;

/**
 * Internal to {@code transfer} - no other module is allowed to write ledger
 * entries. Not exposed as a REST controller; {@code transaction} reads
 * ledger rows for history but never writes them.
 */
public interface LedgerService {

    /**
     * Records the balanced debit/credit pair for one completed transfer.
     * Must be called inside the same transaction that updated both account
     * balances, with the post-update balances passed in so history rows
     * don't need to be recomputed later.
     */
    void recordTransferEntries(
            UUID transferId,
            UUID sourceAccountId,
            long sourceBalanceAfterCents,
            UUID destinationAccountId,
            long destinationBalanceAfterCents,
            long amountCents
    );
}
