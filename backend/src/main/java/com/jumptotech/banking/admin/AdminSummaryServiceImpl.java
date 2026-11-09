package com.jumptotech.banking.admin;

import com.jumptotech.banking.account.AccountRepository;
import com.jumptotech.banking.admin.dto.AdminSummaryDto;
import com.jumptotech.banking.admin.dto.RecentTransferDto;
import com.jumptotech.banking.auth.Role;
import com.jumptotech.banking.auth.UserRepository;
import com.jumptotech.banking.transfer.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminSummaryServiceImpl implements AdminSummaryService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public AdminSummaryServiceImpl(UserRepository userRepository, AccountRepository accountRepository,
                                    TransferRepository transferRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    @Override
    public AdminSummaryDto getSummary() {
        long totalCustomers = userRepository.countByRole(Role.CUSTOMER);
        long totalAccounts = accountRepository.count();
        long totalBalanceCents = accountRepository.sumAllBalances();

        var recentTransfers = transferRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(t -> new RecentTransferDto(t.getId(), t.getAmountCents(), t.getStatus(), t.getCompletedAt()))
                .toList();

        return new AdminSummaryDto(totalCustomers, totalAccounts, totalBalanceCents, recentTransfers);
    }
}
