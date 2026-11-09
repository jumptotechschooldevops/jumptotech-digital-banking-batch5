package com.jumptotech.banking.transaction;

import com.jumptotech.banking.transaction.dto.TransactionDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionQueryService {

    /** Throws if {@code accountId} doesn't exist or isn't owned by {@code userId}. */
    Page<TransactionDto> getHistory(UUID userId, UUID accountId, TransactionFilter filter, Pageable pageable);
}
