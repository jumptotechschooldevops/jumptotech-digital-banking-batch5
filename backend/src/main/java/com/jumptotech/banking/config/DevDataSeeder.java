package com.jumptotech.banking.config;

import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountRepository;
import com.jumptotech.banking.account.AccountType;
import com.jumptotech.banking.auth.Role;
import com.jumptotech.banking.auth.User;
import com.jumptotech.banking.auth.UserRepository;
import com.jumptotech.banking.customer.CustomerProfile;
import com.jumptotech.banking.customer.CustomerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Dev/local-only fictional demo data. Never runs unless
 * {@code banking.seed.enabled=true}, and refuses to run at all if the
 * "prod" profile is active, even if that property were mistakenly set -
 * belt-and-suspenders on top of the property gate itself.
 *
 * No password is ever hardcoded or committed: each seeded user's password
 * is read from an environment variable, and if that variable is unset, a
 * random one is generated and printed once to the application log so local
 * dev can still log in.
 */
@Component
@ConditionalOnProperty(name = "banking.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Environment environment;
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPasswordEnv;
    private final String sarahPasswordEnv;
    private final String michaelPasswordEnv;

    public DevDataSeeder(Environment environment,
                          UserRepository userRepository,
                          CustomerProfileRepository customerProfileRepository,
                          AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${banking.seed.admin-email:}") String adminEmail,
                          @Value("${banking.seed.admin-password:}") String adminPasswordEnv,
                          @Value("${banking.seed.sarah-password:}") String sarahPasswordEnv,
                          @Value("${banking.seed.michael-password:}") String michaelPasswordEnv) {
        this.environment = environment;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPasswordEnv = adminPasswordEnv;
        this.sarahPasswordEnv = sarahPasswordEnv;
        this.michaelPasswordEnv = michaelPasswordEnv;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (List.of(environment.getActiveProfiles()).contains("prod")) {
            log.warn("banking.seed.enabled was true but the 'prod' profile is active - refusing to seed demo data.");
            return;
        }

        seedCustomer("sarah.johnson@example.local", "Sarah", "Johnson", "555-010-1001",
                sarahPasswordEnv, "SEED_SARAH_PASSWORD", 850_000L, 1_520_000L);
        seedCustomer("michael.davis@example.local", "Michael", "Davis", "555-010-1002",
                michaelPasswordEnv, "SEED_MICHAEL_PASSWORD", 420_000L, 1_000_000L);
        seedAdmin();
    }

    private void seedCustomer(String email, String firstName, String lastName, String phone,
                               String passwordEnv, String passwordEnvName,
                               long checkingBalanceCents, long savingsBalanceCents) {
        if (userRepository.existsByEmail(email)) {
            log.info("Seed user {} already exists, skipping.", email);
            return;
        }

        String rawPassword = resolvePassword(passwordEnv, passwordEnvName, email);

        User user = new User(email, passwordEncoder.encode(rawPassword), Role.CUSTOMER);
        user = userRepository.save(user);

        customerProfileRepository.save(new CustomerProfile(user.getId(), firstName, lastName, phone));

        Account checking = new Account(user.getId(), generateAccountNumber(), AccountType.CHECKING);
        checking.credit(checkingBalanceCents);
        Account savings = new Account(user.getId(), generateAccountNumber(), AccountType.SAVINGS);
        savings.credit(savingsBalanceCents);
        accountRepository.save(checking);
        accountRepository.save(savings);

        log.info("Seeded demo customer {} ({} {})", email, firstName, lastName);
    }

    private void seedAdmin() {
        if (!StringUtils.hasText(adminEmail)) {
            log.info("banking.seed.admin-email not set - skipping admin seed user. Set ADMIN_SEED_EMAIL to create one.");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Seed admin {} already exists, skipping.", adminEmail);
            return;
        }

        String rawPassword = resolvePassword(adminPasswordEnv, "ADMIN_SEED_PASSWORD", adminEmail);

        User admin = new User(adminEmail, passwordEncoder.encode(rawPassword), Role.ADMIN);
        admin = userRepository.save(admin);
        customerProfileRepository.save(new CustomerProfile(admin.getId(), "Admin", "User", null));

        log.info("Seeded demo administrator {}", adminEmail);
    }

    private String resolvePassword(String configuredPassword, String envVarName, String forUser) {
        if (StringUtils.hasText(configuredPassword)) {
            return configuredPassword;
        }
        String generated = generateRandomPassword();
        log.warn("{} not set - generated a random local-dev password for {}: {}", envVarName, forUser, generated);
        return generated;
    }

    private static String generateRandomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateAccountNumber() {
        String candidate;
        do {
            candidate = String.format("%010d", Math.abs(RANDOM.nextLong() % 10_000_000_000L));
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
