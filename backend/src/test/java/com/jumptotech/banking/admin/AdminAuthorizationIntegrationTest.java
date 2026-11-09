package com.jumptotech.banking.admin;

import com.jumptotech.banking.AbstractIntegrationTest;
import com.jumptotech.banking.auth.Role;
import com.jumptotech.banking.auth.User;
import com.jumptotech.banking.auth.UserRepository;
import com.jumptotech.banking.customer.CustomerProfile;
import com.jumptotech.banking.customer.CustomerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerProfileRepository customerProfileRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private void createAdmin(String email, String rawPassword) {
        User admin = new User(email, passwordEncoder.encode(rawPassword), Role.ADMIN);
        admin = userRepository.save(admin);
        customerProfileRepository.save(new CustomerProfile(admin.getId(), "Admin", "User", null));
    }

    @Test
    void regularCustomerCannotReachAdminSummary() {
        Session session = newSession();
        String email = uniqueEmail("customer-no-admin");
        register(session, email, "SuperSecret123", "Normal", "Person");
        login(session, email, "SuperSecret123");

        ResponseEntity<String> response = session.get("/api/v1/admin/summary");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanReachAdminSummary() {
        String adminEmail = uniqueEmail("real-admin");
        createAdmin(adminEmail, "AdminSecret123");

        Session session = newSession();
        ResponseEntity<String> loginResponse = login(session, adminEmail, "AdminSecret123");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = session.get("/api/v1/admin/summary");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).has("totalCustomers")).isTrue();
        assertThat(json(response).has("totalAccounts")).isTrue();
        assertThat(json(response).has("totalBalanceCents")).isTrue();
    }

    @Test
    void anonymousRequestToAdminSummaryIsUnauthorized() {
        ResponseEntity<String> response = newSession().get("/api/v1/admin/summary");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
