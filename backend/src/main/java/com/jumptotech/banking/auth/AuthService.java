package com.jumptotech.banking.auth;

import com.jumptotech.banking.auth.dto.AuthUserDto;
import com.jumptotech.banking.auth.dto.RegisterRequest;

import java.util.UUID;

public interface AuthService {

    /** Creates the user, their profile, and a CHECKING + SAVINGS account at $0.00. */
    AuthUserDto register(RegisterRequest request, String ipAddress);

    /** Throws {@link com.jumptotech.banking.common.exception.AccountLockedException} if currently locked out. */
    void assertNotLocked(String email);

    void onLoginSuccess(UUID userId, String ipAddress);

    void onLoginFailure(String email, String ipAddress);
}
