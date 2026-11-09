package com.jumptotech.banking.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.jumptotech.banking.AbstractIntegrationTest;
import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountRepository;
import com.jumptotech.banking.account.AccountStatus;
import com.jumptotech.banking.ledger.EntryType;
import com.jumptotech.banking.ledger.LedgerEntry;
import com.jumptotech.banking.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    TransferRepository transferRepository;

    private record FundedCustomer(Session session, List<UUID> accountIds) {
    }

    private FundedCustomer fundedCustomer(String emailPrefix, long checkingBalanceCents) {
        Session session = newSession();
        String email = uniqueEmail(emailPrefix);
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, email);
        creditAccount(accountIds.get(0), checkingBalanceCents);
        return new FundedCustomer(session, accountIds);
    }

    void creditAccount(UUID accountId, long cents) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.credit(cents);
        accountRepository.save(account);
    }

    @Test
    void successfulTransferDebitsSourceCreditsDestinationAndWritesBalancedLedgerEntries() {
        Session session = newSession();
        String email = uniqueEmail("payer");
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, email);
        UUID checking = accountIds.get(0);
        UUID savings = accountIds.get(1);
        creditAccount(checking, 10_000L); // $100.00

        ResponseEntity<String> response = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", checking, "destinationAccountId", savings, "amountCents", 2_500,
                        "description", "Move to savings"),
                UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json(response);
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.get("sourceAccount").get("balanceCents").asLong()).isEqualTo(7_500L);
        assertThat(body.get("destinationAccount").get("balanceCents").asLong()).isEqualTo(2_500L);

        UUID transferId = UUID.fromString(body.get("transferId").asText());
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransferId(transferId);
        assertThat(entries).hasSize(2);
        long debitTotal = entries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).mapToLong(LedgerEntry::getAmountCents).sum();
        long creditTotal = entries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).mapToLong(LedgerEntry::getAmountCents).sum();
        assertThat(debitTotal).isEqualTo(creditTotal).isEqualTo(2_500L);
    }

    @Test
    void insufficientFundsIsRejectedAndNothingIsWritten() {
        FundedCustomer customer = fundedCustomer("poor", 1_000L); // $10.00
        UUID checking = customer.accountIds().get(0);
        UUID savings = customer.accountIds().get(1);

        ResponseEntity<String> response = customer.session().post("/api/v1/transfers",
                Map.of("sourceAccountId", checking, "destinationAccountId", savings, "amountCents", 50_000),
                UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json(response).get("error").asText()).isEqualTo("INSUFFICIENT_FUNDS");

        Account checkingAfter = accountRepository.findById(checking).orElseThrow();
        assertThat(checkingAfter.getBalanceCents()).isEqualTo(1_000L);
        assertThat(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(checking, org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    @Test
    void invalidAmountIsRejected() {
        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("badamount"));
        creditAccount(accountIds.get(0), 10_000L);

        ResponseEntity<String> zero = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", accountIds.get(1), "amountCents", 0),
                UUID.randomUUID().toString());
        assertThat(zero.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> negative = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", accountIds.get(1), "amountCents", -500),
                UUID.randomUUID().toString());
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cannotTransferFromAnAccountYouDoNotOwn() {
        Session victimSession = newSession();
        List<UUID> victimAccounts = registerLoginAndGetAccountIds(victimSession, uniqueEmail("victim2"));
        creditAccount(victimAccounts.get(0), 10_000L);

        Session attackerSession = newSession();
        List<UUID> attackerAccounts = registerLoginAndGetAccountIds(attackerSession, uniqueEmail("attacker2"));

        ResponseEntity<String> response = attackerSession.post("/api/v1/transfers",
                Map.of("sourceAccountId", victimAccounts.get(0), "destinationAccountId", attackerAccounts.get(0), "amountCents", 1_000),
                UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(response).get("error").asText()).isEqualTo("ACCOUNT_NOT_OWNED");
    }

    @Test
    void transferToNonexistentDestinationIsRejected() {
        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("nodest"));
        creditAccount(accountIds.get(0), 10_000L);

        ResponseEntity<String> response = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", UUID.randomUUID(), "amountCents", 1_000),
                UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void transferToAFrozenDestinationIsRejectedAsIneligible() {
        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("frozen"));
        creditAccount(accountIds.get(0), 10_000L);

        Account savings = accountRepository.findById(accountIds.get(1)).orElseThrow();
        savings.setStatus(AccountStatus.FROZEN);
        accountRepository.save(savings);

        ResponseEntity<String> response = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", accountIds.get(1), "amountCents", 1_000),
                UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json(response).get("error").asText()).isEqualTo("DESTINATION_ACCOUNT_INELIGIBLE");
    }

    @Test
    void duplicateRequestWithSameIdempotencyKeyIsExecutedExactlyOnce() {
        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("idempotent"));
        creditAccount(accountIds.get(0), 10_000L);
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", accountIds.get(1), "amountCents", 3_000);

        ResponseEntity<String> first = session.post("/api/v1/transfers", body, idempotencyKey);
        ResponseEntity<String> second = session.post("/api/v1/transfers", body, idempotencyKey);
        ResponseEntity<String> third = session.post("/api/v1/transfers", body, idempotencyKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK); // replayed, not re-executed
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(first).get("transferId").asText()).isEqualTo(json(second).get("transferId").asText());

        Account checkingAfter = accountRepository.findById(accountIds.get(0)).orElseThrow();
        assertThat(checkingAfter.getBalanceCents()).isEqualTo(7_000L); // debited only once, not three times
        // Scoped to this test's own key - transferRepository.count() is a shared-table-wide
        // total across every test in the run, not something a single test can assert on.
        Transfer transfer = transferRepository.findByIdempotencyKey(UUID.fromString(idempotencyKey)).orElseThrow();
        assertThat(ledgerEntryRepository.findByTransferId(transfer.getId())).hasSize(2); // not 6 - recorded exactly once
    }

    @Test
    void reusingAnIdempotencyKeyWithADifferentPayloadIsAConflict() {
        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("conflict"));
        creditAccount(accountIds.get(0), 10_000L);
        String idempotencyKey = UUID.randomUUID().toString();

        ResponseEntity<String> first = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", accountIds.get(1), "amountCents", 1_000),
                idempotencyKey);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> differentPayload = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", accountIds.get(1), "amountCents", 2_000),
                idempotencyKey);

        assertThat(differentPayload.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(differentPayload).get("error").asText()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("nokey"));
        creditAccount(accountIds.get(0), 10_000L);

        ResponseEntity<String> response = session.post("/api/v1/transfers",
                Map.of("sourceAccountId", accountIds.get(0), "destinationAccountId", accountIds.get(1), "amountCents", 1_000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(response).get("error").asText()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }
}
