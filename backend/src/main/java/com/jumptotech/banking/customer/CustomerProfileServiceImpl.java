package com.jumptotech.banking.customer;

import com.jumptotech.banking.auth.UserDirectory;
import com.jumptotech.banking.customer.dto.CustomerProfileDto;
import com.jumptotech.banking.customer.dto.UpdateCustomerProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@Transactional
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final UserDirectory userDirectory;

    public CustomerProfileServiceImpl(CustomerProfileRepository customerProfileRepository, UserDirectory userDirectory) {
        this.customerProfileRepository = customerProfileRepository;
        this.userDirectory = userDirectory;
    }

    @Override
    public CustomerProfile createProfile(UUID userId, String firstName, String lastName, String phone) {
        CustomerProfile profile = new CustomerProfile(userId, firstName, lastName, phone);
        return customerProfileRepository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileDto getMyProfile(UUID userId) {
        CustomerProfile profile = findByUserIdOrThrow(userId);
        String email = userDirectory.findEmailByUserId(userId).orElseThrow();
        return toDto(profile, email);
    }

    @Override
    public CustomerProfileDto updateProfile(UUID userId, UpdateCustomerProfileRequest request) {
        CustomerProfile profile = findByUserIdOrThrow(userId);

        if (StringUtils.hasText(request.phone())) {
            profile.setPhone(request.phone());
        }
        if (request.addressLine1() != null) {
            profile.setAddressLine1(request.addressLine1());
        }
        if (request.addressLine2() != null) {
            profile.setAddressLine2(request.addressLine2());
        }
        if (request.city() != null) {
            profile.setCity(request.city());
        }
        if (request.state() != null) {
            profile.setState(request.state());
        }
        if (request.postalCode() != null) {
            profile.setPostalCode(request.postalCode());
        }

        CustomerProfile saved = customerProfileRepository.save(profile);
        String email = userDirectory.findEmailByUserId(userId).orElseThrow();
        return toDto(saved, email);
    }

    private CustomerProfile findByUserIdOrThrow(UUID userId) {
        return customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No customer profile for user " + userId));
    }

    private static CustomerProfileDto toDto(CustomerProfile profile, String email) {
        return new CustomerProfileDto(
                profile.getFirstName(),
                profile.getLastName(),
                email,
                profile.getPhone(),
                profile.getAddressLine1(),
                profile.getAddressLine2(),
                profile.getCity(),
                profile.getState(),
                profile.getPostalCode()
        );
    }
}
