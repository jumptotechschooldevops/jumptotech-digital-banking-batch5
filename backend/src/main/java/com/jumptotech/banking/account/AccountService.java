package com.jumptotech.banking.account;

import com.jumptotech.banking.account.dto.AccountDto;

import java.util.List;
import java.util.UUID;

/**
 * Public surface other modules depend on. {@code transfer} is the only
 * module allowed to mutate balances; it does so through
 * {@link #lockAccountsForUpdate(UUID, UUID)} followed by direct entity
 * mutation inside its own transaction, never by writing SQL of its own.
 */
public interface AccountService {

    /** Opens a CHECKING and SAVINGS account for a newly registered customer, both at $0.00. */
    List<Account> createDefaultAccountsForCustomer(UUID userId);

    List<AccountDto> listMyAccounts(UUID userId);

    AccountDto getAccountDto(UUID userId, UUID accountId);

    /** Throws if the account doesn't exist, or exists but isn't owned by {@code userId}. */
    Account getOwnedAccountOrThrow(UUID userId, UUID accountId);

    /** Throws only if the account doesn't exist; no ownership check (used for transfer destinations). */
    Account getAccountOrThrow(UUID accountId);

    /**
     * Locks both accounts with SELECT ... FOR UPDATE in a fixed order (by id)
     * and returns them. Must be called from within an active transaction.
     */
    List<Account> lockAccountsForUpdate(UUID accountIdA, UUID accountIdB);

    AccountDto toDto(Account account);
}
