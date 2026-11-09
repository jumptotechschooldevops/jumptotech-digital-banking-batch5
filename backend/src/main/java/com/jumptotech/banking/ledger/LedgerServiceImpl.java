package com.jumptotech.banking.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LedgerServiceImpl implements LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerServiceImpl(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public void recordTransferEntries(
            UUID transferId,
            UUID sourceAccountId,
            long sourceBalanceAfterCents,
            UUID destinationAccountId,
            long destinationBalanceAfterCents,
            long amountCents
    ) {
        LedgerEntry debit = new LedgerEntry(transferId, sourceAccountId, EntryType.DEBIT, amountCents, sourceBalanceAfterCents);
        LedgerEntry credit = new LedgerEntry(transferId, destinationAccountId, EntryType.CREDIT, amountCents, destinationBalanceAfterCents);
        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        assertBalanced(transferId);
    }

    /**
     * Defense in depth: the two entries above are always constructed with
     * the same amount, so this can only fail if there is a bug elsewhere
     * (e.g. a duplicate entry for this transfer). Verified independently by
     * LedgerInvariantIntegrationTest.
     */
    private void assertBalanced(UUID transferId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransferId(transferId);
        long debitTotal = entries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).mapToLong(LedgerEntry::getAmountCents).sum();
        long creditTotal = entries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).mapToLong(LedgerEntry::getAmountCents).sum();
        if (debitTotal != creditTotal) {
            throw new IllegalStateException("Unbalanced ledger entries for transfer " + transferId);
        }
    }
}
