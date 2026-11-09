package com.jumptotech.banking.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal wrapping our {@link User}. Deliberately does not
 * expose the password hash beyond what {@link UserDetails} requires, and is
 * never serialized back to a client.
 */
public class AuthenticatedPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;

    public AuthenticatedPrincipal(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        // Only DISABLED blocks Spring Security's own login flow; LOCKED (a temporary,
        // time-boxed lockout) is enforced explicitly by AuthService.assertNotLocked so
        // that it can also auto-expire - see that method for why.
        this.enabled = user.getStatus() != UserStatus.DISABLED;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
