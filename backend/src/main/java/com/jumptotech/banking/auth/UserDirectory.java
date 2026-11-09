package com.jumptotech.banking.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * The minimal read-only view of {@code auth} that other modules are allowed
 * to depend on. Keeps e.g. {@code customer} from reaching into
 * {@link UserRepository} directly, so {@code auth} can change its internal
 * storage without breaking other modules.
 */
public interface UserDirectory {
    Optional<String> findEmailByUserId(UUID userId);
}
