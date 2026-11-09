package com.jumptotech.banking.customer;

import com.jumptotech.banking.auth.AuthenticatedPrincipal;
import com.jumptotech.banking.customer.dto.CustomerProfileDto;
import com.jumptotech.banking.customer.dto.UpdateCustomerProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerProfileDto> getMyProfile(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ResponseEntity.ok(customerProfileService.getMyProfile(principal.getUserId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<CustomerProfileDto> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody UpdateCustomerProfileRequest request) {
        return ResponseEntity.ok(customerProfileService.updateProfile(principal.getUserId(), request));
    }
}
