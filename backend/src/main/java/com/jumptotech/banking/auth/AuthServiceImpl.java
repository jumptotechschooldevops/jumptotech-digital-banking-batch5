package com.jumptotech.banking.auth;

import com.jumptotech.banking.account.AccountService;
import com.jumptotech.banking.audit.AuditEventTypes;
import com.jumptotech.banking.audit.AuditService;
import com.jumptotech.banking.auth.dto.AuthUserDto;
import com.jumptotech.banking.auth.dto.RegisterRequest;
import com.jumptotech.banking.common.exception.AccountLockedException;
import com.jumptotech.banking.common.exception.EmailAlreadyRegisteredException;
import com.jumptotech.banking.customer.CustomerProfile;
import com.jumptotech.banking.customer.CustomerProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CustomerProfileService customerProfileService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final int maxFailedAttempts;
    private final long lockoutDurationMinutes;

    public AuthServiceImpl(UserRepository userRepository,
                            CustomerProfileService customerProfileService,
                            AccountService accountService,
                            AuditService auditService,
                            PasswordEncoder passwordEncoder,
                            @Value("${banking.login.max-failed-attempts}") int maxFailedAttempts,
                            @Value("${banking.login.lockout-duration-minutes}") long lockoutDurationMinutes) {
        this.userRepository = userRepository;
        this.customerProfileService = customerProfileService;
        this.accountService = accountService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    @Override
    public AuthUserDto register(RegisterRequest request, String ipAddress) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(email, passwordEncoder.encode(request.password()), Role.CUSTOMER);
        user = userRepository.save(user);

        CustomerProfile profile = customerProfileService.createProfile(
                user.getId(), request.firstName(), request.lastName(), request.phone());

        accountService.createDefaultAccountsForCustomer(user.getId());

        auditService.recordInCurrentTransaction(user.getId(), AuditEventTypes.REGISTRATION_SUCCEEDED, "USER", user.getId(),
                Map.of("email", user.getEmail()), ipAddress);

        return new AuthUserDto(user.getId(), user.getEmail(), user.getRole(), profile.getFirstName(), profile.getLastName());
    }

    @Override
    public void assertNotLocked(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(EmailNormalizer.normalize(email));
        if (maybeUser.isEmpty()) {
            return; // don't reveal whether the email exists
        }
        User user = maybeUser.get();
        if (user.getStatus() != UserStatus.LOCKED) {
            return;
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException();
        }
        // Lockout window has expired - clear it now so the upcoming authentication
        // attempt (and AuthenticatedPrincipal.isEnabled()) see a consistent state.
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    @Override
    public void onLoginSuccess(java.util.UUID userId, String ipAddress) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
        auditService.record(userId, AuditEventTypes.LOGIN_SUCCEEDED, "USER", userId, null, ipAddress);
    }

    @Override
    public void onLoginFailure(String email, String ipAddress) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            auditService.record(null, AuditEventTypes.LOGIN_FAILED, "USER", null, Map.of("email", normalizedEmail), ipAddress);
            return;
        }
        User user = maybeUser.get();
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(Instant.now().plusSeconds(lockoutDurationMinutes * 60));
        }
        userRepository.save(user);
        auditService.record(user.getId(), AuditEventTypes.LOGIN_FAILED, "USER", user.getId(),
                Map.of("failedLoginAttempts", attempts), ipAddress);
    }
}
