package com.jumptotech.banking.account;

import tools.jackson.databind.JsonNode;
import com.jumptotech.banking.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountOwnershipIntegrationTest extends AbstractIntegrationTest {

    @Test
    void customerCanReadTheirOwnAccountBalance() {
        Session session = newSession();
        String email = uniqueEmail("owner");
        register(session, email, "SuperSecret123", "Grace", "Hopper");
        login(session, email, "SuperSecret123");

        JsonNode accounts = json(session.get("/api/v1/accounts"));
        UUID accountId = UUID.fromString(accounts.get(0).get("id").asText());

        ResponseEntity<String> response = session.get("/api/v1/accounts/" + accountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).get("balanceCents").asLong()).isZero();
        assertThat(json(response).get("maskedAccountNumber").asText()).startsWith("****");
    }

    @Test
    void customerCannotReadAnotherCustomersAccount() {
        Session ownerSession = newSession();
        String ownerEmail = uniqueEmail("victim");
        register(ownerSession, ownerEmail, "SuperSecret123", "Victim", "Owner");
        login(ownerSession, ownerEmail, "SuperSecret123");
        JsonNode ownerAccounts = json(ownerSession.get("/api/v1/accounts"));
        UUID victimAccountId = UUID.fromString(ownerAccounts.get(0).get("id").asText());

        Session attackerSession = newSession();
        String attackerEmail = uniqueEmail("attacker");
        register(attackerSession, attackerEmail, "SuperSecret123", "Attacker", "Person");
        login(attackerSession, attackerEmail, "SuperSecret123");

        ResponseEntity<String> response = attackerSession.get("/api/v1/accounts/" + victimAccountId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(response).get("error").asText()).isEqualTo("ACCOUNT_NOT_OWNED");
    }

    @Test
    void accessingANonexistentAccountReturnsNotFound() {
        Session session = newSession();
        String email = uniqueEmail("ghost");
        register(session, email, "SuperSecret123", "Ghost", "Account");
        login(session, email, "SuperSecret123");

        ResponseEntity<String> response = session.get("/api/v1/accounts/" + UUID.randomUUID());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listingAccountsRequiresAuthentication() {
        Session session = newSession();
        ResponseEntity<String> response = session.get("/api/v1/accounts");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
