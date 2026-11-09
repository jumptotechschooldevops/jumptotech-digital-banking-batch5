package com.jumptotech.banking.account;

import com.jumptotech.banking.account.dto.AccountDto;
import com.jumptotech.banking.common.exception.ForbiddenOperationException;
import com.jumptotech.banking.common.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    public AccountServiceImpl(AccountRepository accountRepository, EntityManager entityManager) {
        this.accountRepository = accountRepository;
        this.entityManager = entityManager;
    }

    @Override
    public List<Account> createDefaultAccountsForCustomer(UUID userId) {
        Account checking = new Account(userId, generateUniqueAccountNumber(), AccountType.CHECKING);
        Account savings = new Account(userId, generateUniqueAccountNumber(), AccountType.SAVINGS);
        return List.of(accountRepository.save(checking), accountRepository.save(savings));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> listMyAccounts(UUID userId) {
        return accountRepository.findByOwnerUserIdOrderByAccountTypeAsc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccountDto(UUID userId, UUID accountId) {
        return toDto(getOwnedAccountOrThrow(userId, accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public Account getOwnedAccountOrThrow(UUID userId, UUID accountId) {
        Account account = getAccountOrThrow(accountId);
        if (!account.isOwnedBy(userId)) {
            // 403, not 404 - avoids letting a client distinguish "not yours" from "doesn't exist".
            throw new ForbiddenOperationException("ACCOUNT_NOT_OWNED", "This account does not belong to you.");
        }
        return account;
    }

    @Override
    @Transactional(readOnly = true)
    public Account getAccountOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found."));
    }

    @Override
    public List<Account> lockAccountsForUpdate(UUID accountIdA, UUID accountIdB) {
        List<Account> locked = accountRepository.lockAllByIdForUpdate(List.of(accountIdA, accountIdB));
        // If either account was already loaded earlier in this transaction (e.g. by the
        // ownership/eligibility checks that ran before locking), Hibernate's identity map
        // would otherwise hand back that *same* Java object with its old, pre-lock field
        // values instead of re-reading them - meaning the lock serializes the writes
        // correctly, but the balance we then read and debit from is stale. Forcing a refresh
        // while the lock is held is what makes "read balance, then write" actually safe.
        locked.forEach(entityManager::refresh);
        return locked;
    }

    @Override
    public AccountDto toDto(Account account) {
        return new AccountDto(
                account.getId(),
                account.maskedAccountNumber(),
                account.getAccountType(),
                account.getBalanceCents(),
                account.getCurrency(),
                account.getStatus()
        );
    }

    private String generateUniqueAccountNumber() {
        String candidate;
        do {
            candidate = String.format("%010d", Math.abs(RANDOM.nextLong() % 10_000_000_000L));
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
