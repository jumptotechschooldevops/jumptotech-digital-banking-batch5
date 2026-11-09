package com.jumptotech.banking.transaction;

import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountService;
import com.jumptotech.banking.common.exception.ResourceNotFoundException;
import com.jumptotech.banking.ledger.LedgerEntry;
import com.jumptotech.banking.ledger.LedgerEntryRepository;
import com.jumptotech.banking.transaction.dto.TransactionDto;
import com.jumptotech.banking.transfer.Transfer;
import com.jumptotech.banking.transfer.TransferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionQueryServiceImpl implements TransactionQueryService {

    private static final Instant EPOCH = Instant.EPOCH;
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferRepository transferRepository;

    public TransactionQueryServiceImpl(AccountService accountService,
                                        LedgerEntryRepository ledgerEntryRepository,
                                        TransferRepository transferRepository) {
        this.accountService = accountService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transferRepository = transferRepository;
    }

    @Override
    public Page<TransactionDto> getHistory(UUID userId, UUID accountId, TransactionFilter filter, Pageable pageable) {
        // Ownership check - throws 403/404 before any ledger data is touched.
        accountService.getOwnedAccountOrThrow(userId, accountId);

        Instant from = filter.from() != null ? filter.from() : EPOCH;
        Instant to = filter.to() != null ? filter.to() : FAR_FUTURE;

        Page<LedgerEntry> entries = filter.type() != null
                ? ledgerEntryRepository.findByAccountIdAndEntryTypeAndCreatedAtBetweenOrderByCreatedAtDesc(accountId, filter.type(), from, to, pageable)
                : ledgerEntryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountId, from, to, pageable);

        return entries.map(entry -> toDto(accountId, entry));
    }

    private TransactionDto toDto(UUID accountId, LedgerEntry entry) {
        Transfer transfer = transferRepository.findById(entry.getTransferId())
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer referenced by ledger entry not found."));

        UUID counterpartyAccountId = transfer.getSourceAccountId().equals(accountId)
                ? transfer.getDestinationAccountId()
                : transfer.getSourceAccountId();
        Account counterparty = accountService.getAccountOrThrow(counterpartyAccountId);

        return new TransactionDto(
                entry.getId(),
                transfer.getId(),
                entry.getCreatedAt(),
                entry.getEntryType(),
                entry.getAmountCents(),
                entry.getBalanceAfterCents(),
                transfer.getStatus(),
                counterparty.maskedAccountNumber(),
                transfer.getDescription()
        );
    }
}
