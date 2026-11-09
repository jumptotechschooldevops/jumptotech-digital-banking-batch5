package com.jumptotech.banking.admin.dto;

import java.util.List;

public record AdminSummaryDto(
        long totalCustomers,
        long totalAccounts,
        long totalBalanceCents,
        List<RecentTransferDto> recentTransfers
) {
}
