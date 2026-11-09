package com.jumptotech.banking.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountService;
import com.jumptotech.banking.common.exception.ForbiddenOperationException;
import com.jumptotech.banking.common.exception.IdempotencyKeyConflictException;
import com.jumptotech.banking.common.exception.InvalidIdempotencyKeyException;
import com.jumptotech.banking.common.exception.ResourceNotFoundException;
import com.jumptotech.banking.transfer.dto.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferServiceImpl implements TransferService {

    private final TransferExecutionService transferExecutionService;
    private final TransferRepository transferRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    public TransferServiceImpl(TransferExecutionService transferExecutionService,
                                TransferRepository transferRepository,
                                IdempotencyRecordRepository idempotencyRecordRepository,
                                AccountService accountService,
                                ObjectMapper objectMapper) {
        this.transferExecutionService = transferExecutionService;
        this.transferRepository = transferRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
    }

    @Override
    public TransferExecutionResult executeTransfer(UUID userId, CreateTransferRequest request,
                                                     String idempotencyKeyHeader, String ipAddress) {
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        String requestHash = hash(userId, request);

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new TransferExecutionResult(replay(existing.get(), requestHash), true);
        }

        try {
            TransferConfirmationDto fresh = transferExecutionService.execute(userId, request, idempotencyKey, requestHash, ipAddress);
            return new TransferExecutionResult(fresh, false);
        } catch (DataIntegrityViolationException raceLost) {
            // Another concurrent request with the same idempotency key committed first.
            // Our own transaction has already been rolled back by Spring at this point.
            IdempotencyRecord winner = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> raceLost);
            return new TransferExecutionResult(replay(winner, requestHash), true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransferDetailDto getTransfer(UUID userId, UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found."));

        Account source = accountService.getAccountOrThrow(transfer.getSourceAccountId());
        Account destination = accountService.getAccountOrThrow(transfer.getDestinationAccountId());

        if (!source.isOwnedBy(userId) && !destination.isOwnedBy(userId)) {
            throw new ForbiddenOperationException("TRANSFER_NOT_ACCESSIBLE", "This transfer does not belong to you.");
        }

        return new TransferDetailDto(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getAmountCents(),
                transfer.getDescription(),
                new TransferAccountSummaryDto(source.getId(), source.maskedAccountNumber(), source.getBalanceCents()),
                new TransferAccountSummaryDto(destination.getId(), destination.maskedAccountNumber(), destination.getBalanceCents()),
                transfer.getCreatedAt(),
                transfer.getCompletedAt()
        );
    }

    private TransferConfirmationDto replay(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException();
        }
        return objectMapper.convertValue(record.getResponseBody(), TransferConfirmationDto.class);
    }

    private static UUID parseIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            throw new InvalidIdempotencyKeyException();
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidIdempotencyKeyException();
        }
    }

    private static String hash(UUID userId, CreateTransferRequest request) {
        String raw = userId + "|" + request.sourceAccountId() + "|" + request.destinationAccountId()
                + "|" + request.amountCents() + "|" + (request.description() == null ? "" : request.description());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
