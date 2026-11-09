package com.jumptotech.banking.auth;

import com.jumptotech.banking.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registrationCreatesCustomerWithTwoZeroBalanceAccounts() {
        Session session = newSession();
        String email = uniqueEmail("register");

        ResponseEntity<String> response = register(session, email, "SuperSecret123", "Alice", "Wonder");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(response).get("email").asText()).isEqualToIgnoringCase(email);
        assertThat(json(response).get("role").asText()).isEqualTo("CUSTOMER");

        login(session, email, "SuperSecret123");
        ResponseEntity<String> accounts = session.get("/api/v1/accounts");
        assertThat(accounts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(accounts)).hasSize(2);
        json(accounts).forEach(a -> assertThat(a.get("balanceCents").asLong()).isZero());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        Session session = newSession();
        String email = uniqueEmail("dup");
        register(session, email, "SuperSecret123", "Bob", "Builder");

        ResponseEntity<String> second = register(newSession(), email, "SuperSecret123", "Bob", "Builder");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(second).get("error").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void successfulLoginReturnsUserAndEstablishesSession() {
        Session session = newSession();
        String email = uniqueEmail("login-ok");
        register(session, email, "SuperSecret123", "Carol", "Danvers");

        ResponseEntity<String> loginResponse = login(session, email, "SuperSecret123");

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(loginResponse).get("email").asText()).isEqualToIgnoringCase(email);

        ResponseEntity<String> me = session.get("/api/v1/auth/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(me).get("firstName").asText()).isEqualTo("Carol");
    }

    @Test
    void wrongPasswordReturnsGenericInvalidCredentials() {
        Session session = newSession();
        String email = uniqueEmail("login-bad");
        register(session, email, "SuperSecret123", "Dan", "Extra");

        ResponseEntity<String> response = login(newSession(), email, "WrongPassword999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(response).get("error").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void unknownEmailReturnsTheSameGenericInvalidCredentials() {
        ResponseEntity<String> response = login(newSession(), uniqueEmail("nobody"), "WhateverPassword1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(response).get("error").asText()).isEqualTo("INVALID_CREDENTIALS");
        // Same message whether or not the account exists - must not leak which case it was.
        assertThat(json(response).get("message").asText()).isEqualTo("Invalid email or password.");
    }

    @Test
    void repeatedFailedLoginsLockTheAccount() {
        Session registerSession = newSession();
        String email = uniqueEmail("lockout");
        register(registerSession, email, "SuperSecret123", "Eve", "Locked");

        ResponseEntity<String> last = null;
        for (int i = 0; i < 5; i++) {
            last = login(newSession(), email, "DefinitelyWrong1");
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The 6th attempt (even with the correct password) must now be rejected as locked.
        ResponseEntity<String> lockedAttempt = login(newSession(), email, "SuperSecret123");
        assertThat(lockedAttempt.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(json(lockedAttempt).get("error").asText()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void getMeWithoutSessionIsUnauthorized() {
        Session session = newSession();
        ResponseEntity<String> response = session.get("/api/v1/auth/me");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutInvalidatesSession() {
        Session session = newSession();
        String email = uniqueEmail("logout");
        register(session, email, "SuperSecret123", "Frank", "Out");
        login(session, email, "SuperSecret123");

        assertThat(session.get("/api/v1/auth/me").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> logoutResponse = session.post("/api/v1/auth/logout", null);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(session.get("/api/v1/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registrationWithoutCsrfTokenIsRejected() {
        // Posting without ever having primed the XSRF-TOKEN cookie must fail CSRF validation.
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/auth/register",
                Map.of("email", uniqueEmail("nocsrf"), "password", "SuperSecret123", "firstName", "No", "lastName", "Csrf"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
