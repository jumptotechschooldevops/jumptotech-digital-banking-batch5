package com.jumptotech.banking.customer;

import com.jumptotech.banking.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerProfileIntegrationTest extends AbstractIntegrationTest {

    @Test
    void customerCanViewAndUpdateTheirOwnProfile() {
        Session session = newSession();
        String email = uniqueEmail("profile");
        register(session, email, "SuperSecret123", "Nina", "Simone");
        login(session, email, "SuperSecret123");

        ResponseEntity<String> getResponse = session.get("/api/v1/customers/me");
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(getResponse).get("firstName").asText()).isEqualTo("Nina");

        ResponseEntity<String> patchResponse = session.patch("/api/v1/customers/me",
                Map.of("city", "New Orleans", "state", "LA", "postalCode", "70112"));
        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(patchResponse).get("city").asText()).isEqualTo("New Orleans");

        // firstName/lastName/email are not patchable through this endpoint - still the originals.
        assertThat(json(patchResponse).get("firstName").asText()).isEqualTo("Nina");
    }

    @Test
    void profileEndpointsRequireAuthentication() {
        ResponseEntity<String> response = newSession().get("/api/v1/customers/me");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
