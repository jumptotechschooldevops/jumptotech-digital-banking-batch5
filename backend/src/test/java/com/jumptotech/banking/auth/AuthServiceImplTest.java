package com.jumptotech.banking.auth;

import com.jumptotech.banking.account.AccountService;
import com.jumptotech.banking.audit.AuditService;
import com.jumptotech.banking.common.exception.AccountLockedException;
import com.jumptotech.banking.customer.CustomerProfile;
import com.jumptotech.banking.customer.CustomerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    UserRepository userRepository;
    @Mock
    CustomerProfileService customerProfileService;
    @Mock
    AccountService accountService;
    @Mock
    AuditService auditService;
    @Mock
    PasswordEncoder passwordEncoder;

    AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, customerProfileService, accountService,
                auditService, passwordEncoder, 5, 15);
    }

    private User userWith(String email, int failedAttempts, UserStatus status, Instant lockedUntil) {
        User user = new User(email, "hash", Role.CUSTOMER);
        user.setId(UUID.randomUUID());
        user.setFailedLoginAttempts(failedAttempts);
        user.setStatus(status);
        user.setLockedUntil(lockedUntil);
        return user;
    }

    @Test
    void assertNotLockedDoesNothingForAnUnknownEmail() {
        when(userRepository.findByEmail("ghost@example.test")).thenReturn(Optional.empty());

        authService.assertNotLocked("ghost@example.test");

        verify(userRepository, never()).save(any());
    }

    @Test
    void assertNotLockedThrowsWhileTheLockoutWindowIsStillActive() {
        User locked = userWith("locked@example.test", 5, UserStatus.LOCKED, Instant.now().plusSeconds(600));
        when(userRepository.findByEmail("locked@example.test")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> authService.assertNotLocked("locked@example.test"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void assertNotLockedAutoUnlocksOnceTheWindowHasExpired() {
        User expiredLock = userWith("expired@example.test", 5, UserStatus.LOCKED, Instant.now().minusSeconds(1));
        when(userRepository.findByEmail("expired@example.test")).thenReturn(Optional.of(expiredLock));

        authService.assertNotLocked("expired@example.test");

        assertThat(expiredLock.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(expiredLock.getFailedLoginAttempts()).isZero();
        verify(userRepository).save(expiredLock);
    }

    @Test
    void onLoginFailureLocksTheAccountOnceTheThresholdIsReached() {
        User user = userWith("almost@example.test", 4, UserStatus.ACTIVE, null);
        when(userRepository.findByEmail("almost@example.test")).thenReturn(Optional.of(user));

        authService.onLoginFailure("almost@example.test", "127.0.0.1");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void onLoginFailureBelowThresholdDoesNotLock() {
        User user = userWith("fine@example.test", 1, UserStatus.ACTIVE, null);
        when(userRepository.findByEmail("fine@example.test")).thenReturn(Optional.of(user));

        authService.onLoginFailure("fine@example.test", "127.0.0.1");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void onLoginSuccessResetsFailedAttemptsAndUnlocks() {
        User user = userWith("recovering@example.test", 4, UserStatus.LOCKED, Instant.now().minusSeconds(1));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.onLoginSuccess(user.getId(), "127.0.0.1");

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        when(userRepository.existsByEmail("taken@example.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new com.jumptotech.banking.auth.dto.RegisterRequest("taken@example.test", "SuperSecret123", "A", "B", null),
                "127.0.0.1"))
                .isInstanceOf(com.jumptotech.banking.common.exception.EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerCreatesUserProfileAndDefaultAccounts() {
        when(userRepository.existsByEmail("new@example.test")).thenReturn(false);
        when(passwordEncoder.encode("SuperSecret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), "New", "Person", null);
        when(customerProfileService.createProfile(any(), eq("New"), eq("Person"), any())).thenReturn(profile);

        var dto = authService.register(
                new com.jumptotech.banking.auth.dto.RegisterRequest("new@example.test", "SuperSecret123", "New", "Person", null),
                "127.0.0.1");

        assertThat(dto.email()).isEqualTo("new@example.test");
        assertThat(dto.role()).isEqualTo(Role.CUSTOMER);
        verify(accountService).createDefaultAccountsForCustomer(any());
    }
}
