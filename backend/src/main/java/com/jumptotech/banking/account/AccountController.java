package com.jumptotech.banking.account;

import com.jumptotech.banking.account.dto.AccountDto;
import com.jumptotech.banking.auth.AuthenticatedPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<AccountDto>> listMyAccounts(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(accountService.listMyAccounts(principal.getUserId()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDto> getAccount(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getAccountDto(principal.getUserId(), accountId));
    }
}
