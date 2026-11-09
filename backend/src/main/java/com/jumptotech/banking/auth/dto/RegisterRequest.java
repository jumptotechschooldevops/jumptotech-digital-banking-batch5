package com.jumptotech.banking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "must be a valid email address") String email,
        @NotBlank @Size(min = 10, max = 72, message = "must be at least 10 characters") String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^[0-9+()\\-. ]{7,20}$", message = "must be a valid phone number") String phone
) {
}
