package com.jumptotech.banking.config;

import com.jumptotech.banking.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthEndpointIsPubliclyReachableAndUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).get("status").asText()).isEqualTo("UP");
    }
}
