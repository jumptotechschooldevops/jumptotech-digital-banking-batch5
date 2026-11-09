package com.jumptotech.banking.admin;

import com.jumptotech.banking.admin.dto.AdminSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authorization is enforced declaratively in SecurityConfig (ADMIN role required for /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminSummaryService adminSummaryService;

    public AdminController(AdminSummaryService adminSummaryService) {
        this.adminSummaryService = adminSummaryService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminSummaryDto> getSummary() {
        return ResponseEntity.ok(adminSummaryService.getSummary());
    }
}
