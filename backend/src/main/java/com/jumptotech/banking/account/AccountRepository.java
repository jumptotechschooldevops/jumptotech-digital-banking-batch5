package com.jumptotech.banking.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerUserIdOrderByAccountTypeAsc(UUID ownerUserId);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Locks the given accounts with SELECT ... FOR UPDATE, always ordered by
     * primary key ascending regardless of the order the ids are passed in.
     * This fixed lock order is what prevents deadlocks between two concurrent
     * transfers moving money in opposite directions between the same pair of
     * accounts - see docs/database.md "Locking strategy for transfers".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id asc")
    List<Account> lockAllByIdForUpdate(@Param("ids") List<UUID> ids);

    Optional<Account> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Query("select coalesce(sum(a.balanceCents), 0) from Account a")
    long sumAllBalances();
}
