package com.jumptotech.banking.auth.dto;

import com.jumptotech.banking.auth.Role;

import java.util.UUID;

public record AuthUserDto(
        UUID id,
        String email,
        Role role,
        String firstName,
        String lastName
) {
}
