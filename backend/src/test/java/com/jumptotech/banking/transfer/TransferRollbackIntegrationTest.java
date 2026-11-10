package com.jumptotech.banking.transfer;

import com.jumptotech.banking.AbstractIntegrationTest;
import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountRepository;
import com.jumptotech.banking.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;

/**
 * Proves the transfer is truly atomic: if anything fails after the account
 * balances have already been mutated in memory but before the surrounding
 * @Transactional method commits, every write - balances, the transfer row,
 * and any idempotency record - is rolled back together. We force that
 * failure by replacing LedgerService with a mock that throws, since in the
 * real code path ledger recording is the last step before commit.
 */
class TransferRollbackIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    LedgerService ledgerService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRepository transferRepository;

    @Test
    void aFailureAfterBalancesAreMutatedRollsBackTheEntireTransfer() {
        Mockito.doThrow(new RuntimeException("simulated ledger failure"))
                .when(ledgerService)
                .recordTransferEntries(any(), any(), anyLong(), any(), anyLong(), anyLong());

        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("rollback"));
        UUID checking = accountIds.get(0);
        UUID savings = accountIds.get(1);
        creditDirectly(checking, 10_000L);

        long transfersBefore = transferRepository.count();

        ResponseEntity<String> response = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", checking, "destinationAccountId", savings, "amountCents", 4_000),
                UUID.randomUUID().toString());

        // The ledger failure surfaces as an unhandled 500 - what matters for this test is the DB state after it.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        Account checkingAfter = accountRepository.findById(checking).orElseThrow();
        Account savingsAfter = accountRepository.findById(savings).orElseThrow();
        assertThat(checkingAfter.getBalanceCents()).isEqualTo(10_000L); // unchanged - debit was rolled back
        assertThat(savingsAfter.getBalanceCents()).isZero();            // unchanged - credit was rolled back
        assertThat(transferRepository.count()).isEqualTo(transfersBefore); // no Transfer row was left behind
    }

    private void creditDirectly(UUID accountId, long cents) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.credit(cents);
        accountRepository.save(account);
    }
}
