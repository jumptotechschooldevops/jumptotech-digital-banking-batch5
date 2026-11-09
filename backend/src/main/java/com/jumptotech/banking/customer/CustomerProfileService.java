package com.jumptotech.banking.customer;

import com.jumptotech.banking.customer.dto.CustomerProfileDto;
import com.jumptotech.banking.customer.dto.UpdateCustomerProfileRequest;

import java.util.UUID;

public interface CustomerProfileService {

    CustomerProfile createProfile(UUID userId, String firstName, String lastName, String phone);

    CustomerProfileDto getMyProfile(UUID userId);

    CustomerProfileDto updateProfile(UUID userId, UpdateCustomerProfileRequest request);
}
