package com.jumptotech.banking.customer.dto;

public record CustomerProfileDto(
        String firstName,
        String lastName,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode
) {
}
