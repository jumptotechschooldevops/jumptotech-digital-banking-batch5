package com.jumptotech.banking.auth;

import java.util.Locale;

/**
 * Email is stored lowercased so a plain unique column gives case-insensitive
 * uniqueness and lookup without depending on the Postgres citext extension.
 */
public final class EmailNormalizer {

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private EmailNormalizer() {
    }
}
