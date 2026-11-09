package com.jumptotech.banking.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountService;
import com.jumptotech.banking.account.AccountStatus;
import com.jumptotech.banking.audit.AuditEventTypes;
import com.jumptotech.banking.audit.AuditService;
import com.jumptotech.banking.common.exception.BankingException;
import com.jumptotech.banking.common.exception.DestinationAccountIneligibleException;
import com.jumptotech.banking.common.exception.InsufficientFundsException;
import com.jumptotech.banking.common.exception.InvalidTransferAmountException;
import com.jumptotech.banking.ledger.LedgerService;
import com.jumptotech.banking.transfer.dto.CreateTransferRequest;
import com.jumptotech.banking.transfer.dto.TransferAccountSummaryDto;
import com.jumptotech.banking.transfer.dto.TransferConfirmationDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only class in the application allowed to debit/credit an account
 * balance. Deliberately a separate Spring bean (not a method on
 * {@link TransferServiceImpl}) so its {@code @Transactional} boundary is
 * enforced by the AOP proxy - a self-invoked method on the same bean would
 * silently skip the proxy and the transaction guarantee with it.
 */
@Service
class TransferExecutionService {

    private static final String ENDPOINT = "POST /api/v1/transfers";
    private static final long IDEMPOTENCY_RECORD_TTL_DAYS = 30;

    private final AccountService accountService;
    private final TransferRepository transferRepository;
    private final LedgerService ledgerService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    TransferExecutionService(AccountService accountService,
                              TransferRepository transferRepository,
                              LedgerService ledgerService,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              AuditService auditService,
                              ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.transferRepository = transferRepository;
        this.ledgerService = ledgerService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    TransferConfirmationDto execute(UUID userId, CreateTransferRequest request,
                                     UUID idempotencyKey, String requestHash, String ipAddress) {
        try {
            TransferConfirmationDto result = doExecute(userId, request, idempotencyKey);
            persistIdempotencyRecord(idempotencyKey, userId, requestHash, result);
            auditService.record(userId, AuditEventTypes.TRANSFER_COMPLETED, "TRANSFER", result.transferId(),
                    Map.of("amountCents", result.amountCents(),
                            "sourceAccountId", result.sourceAccount().id(),
                            "destinationAccountId", result.destinationAccount().id()),
                    ipAddress);
            return result;
        } catch (BankingException ex) {
            auditService.record(userId, AuditEventTypes.TRANSFER_REJECTED, "TRANSFER", null,
                    Map.of("reason", ex.getErrorCode(),
                            "sourceAccountId", request.sourceAccountId().toString(),
                            "destinationAccountId", request.destinationAccountId().toString(),
                            "amountCents", request.amountCents()),
                    ipAddress);
            throw ex;
        }
    }

    private TransferConfirmationDto doExecute(UUID userId, CreateTransferRequest request, UUID idempotencyKey) {
        long amountCents = request.amountCents();
        if (amountCents <= 0) {
            throw new InvalidTransferAmountException();
        }

        // Ownership of the source is checked against the database - never trust the request body.
        Account source = accountService.getOwnedAccountOrThrow(userId, request.sourceAccountId());
        Account destination = accountService.getAccountOrThrow(request.destinationAccountId());

        if (source.getId().equals(destination.getId())) {
            throw new DestinationAccountIneligibleException("Source and destination accounts must be different.");
        }
        if (destination.getStatus() != AccountStatus.ACTIVE) {
            throw new DestinationAccountIneligibleException("The destination account cannot receive transfers.");
        }

        // Lock both rows in a fixed order (by id) so two transfers in opposite
        // directions between the same pair of accounts can never deadlock.
        List<Account> locked = accountService.lockAccountsForUpdate(source.getId(), destination.getId());
        Account lockedSource = pick(locked, source.getId());
        Account lockedDestination = pick(locked, destination.getId());

        if (lockedSource.getBalanceCents() < amountCents) {
            throw new InsufficientFundsException();
        }

        lockedSource.debit(amountCents);
        lockedDestination.credit(amountCents);

        Transfer transfer = new Transfer(idempotencyKey, lockedSource.getId(), lockedDestination.getId(),
                amountCents, request.description(), userId);
        transfer.markCompleted();
        transfer = transferRepository.save(transfer);

        ledgerService.recordTransferEntries(
                transfer.getId(),
                lockedSource.getId(), lockedSource.getBalanceCents(),
                lockedDestination.getId(), lockedDestination.getBalanceCents(),
                amountCents);

        return new TransferConfirmationDto(
                transfer.getId(),
                transfer.getStatus(),
                amountCents,
                new TransferAccountSummaryDto(lockedSource.getId(), lockedSource.maskedAccountNumber(), lockedSource.getBalanceCents()),
                new TransferAccountSummaryDto(lockedDestination.getId(), lockedDestination.maskedAccountNumber(), lockedDestination.getBalanceCents()),
                transfer.getCompletedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private void persistIdempotencyRecord(UUID idempotencyKey, UUID userId, String requestHash, TransferConfirmationDto result) {
        Map<String, Object> responseBody = objectMapper.convertValue(result, Map.class);
        IdempotencyRecord record = new IdempotencyRecord(
                idempotencyKey, userId, ENDPOINT, requestHash, 201, responseBody,
                Instant.now().plus(IDEMPOTENCY_RECORD_TTL_DAYS, ChronoUnit.DAYS));
        idempotencyRecordRepository.save(record);
    }

    private static Account pick(List<Account> accounts, UUID id) {
        return accounts.stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected locked account " + id + " not returned by lock query"));
    }
}
