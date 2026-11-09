package com.jumptotech.banking.transaction;

import com.jumptotech.banking.auth.AuthenticatedPrincipal;
import com.jumptotech.banking.common.web.PageResponse;
import com.jumptotech.banking.ledger.EntryType;
import com.jumptotech.banking.transaction.dto.TransactionDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionQueryService transactionQueryService;

    public TransactionController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TransactionDto>> getHistory(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) EntryType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("createdAt").descending());
        TransactionFilter filter = new TransactionFilter(type, from, to);
        var result = transactionQueryService.getHistory(principal.getUserId(), accountId, filter, pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }
}
