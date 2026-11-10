package com.jumptotech.banking.transfer;

import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountService;
import com.jumptotech.banking.account.AccountStatus;
import com.jumptotech.banking.account.AccountType;
import com.jumptotech.banking.audit.AuditService;
import com.jumptotech.banking.common.exception.DestinationAccountIneligibleException;
import com.jumptotech.banking.common.exception.ForbiddenOperationException;
import com.jumptotech.banking.common.exception.InsufficientFundsException;
import com.jumptotech.banking.common.exception.InvalidTransferAmountException;
import com.jumptotech.banking.ledger.LedgerService;
import com.jumptotech.banking.transfer.dto.CreateTransferRequest;
import com.jumptotech.banking.transfer.dto.TransferConfirmationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferExecutionServiceTest {

    @Mock
    AccountService accountService;
    @Mock
    TransferRepository transferRepository;
    @Mock
    LedgerService ledgerService;
    @Mock
    IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    AuditService auditService;

    TransferExecutionService service;

    UUID userId;
    Account source;
    Account destination;

    @BeforeEach
    void setUp() {
        service = new TransferExecutionService(accountService, transferRepository, ledgerService,
                idempotencyRecordRepository, auditService, new ObjectMapper());

        userId = UUID.randomUUID();
        source = new Account(userId, "1000000001", AccountType.CHECKING);
        source.setId(UUID.randomUUID());
        source.credit(10_000L);

        destination = new Account(UUID.randomUUID(), "1000000002", AccountType.SAVINGS);
        destination.setId(UUID.randomUUID());

        lenient().when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
    }

    private CreateTransferRequest request(long amountCents) {
        return new CreateTransferRequest(source.getId(), destination.getId(), amountCents, "test transfer");
    }

    @Test
    void rejectsNonPositiveAmountBeforeTouchingAnyAccount() {
        assertThatThrownBy(() -> service.execute(userId, request(0), UUID.randomUUID(), "hash", "127.0.0.1"))
                .isInstanceOf(InvalidTransferAmountException.class);

        verifyNoInteractions(accountService, ledgerService, transferRepository);
    }

    @Test
    void rejectsWhenCallerDoesNotOwnTheSourceAccount() {
        when(accountService.getOwnedAccountOrThrow(userId, source.getId()))
                .thenThrow(new ForbiddenOperationException("ACCOUNT_NOT_OWNED", "not yours"));

        assertThatThrownBy(() -> service.execute(userId, request(1_000), UUID.randomUUID(), "hash", "127.0.0.1"))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(ledgerService);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void rejectsTransferToAFrozenDestination() {
        destination.setStatus(AccountStatus.FROZEN);
        when(accountService.getOwnedAccountOrThrow(userId, source.getId())).thenReturn(source);
        when(accountService.getAccountOrThrow(destination.getId())).thenReturn(destination);

        assertThatThrownBy(() -> service.execute(userId, request(1_000), UUID.randomUUID(), "hash", "127.0.0.1"))
                .isInstanceOf(DestinationAccountIneligibleException.class);

        verifyNoInteractions(ledgerService);
    }

    @Test
    void rejectsSourceAndDestinationBeingTheSameAccount() {
        when(accountService.getOwnedAccountOrThrow(userId, source.getId())).thenReturn(source);
        when(accountService.getAccountOrThrow(source.getId())).thenReturn(source);

        CreateTransferRequest sameAccount = new CreateTransferRequest(source.getId(), source.getId(), 1_000L, null);

        assertThatThrownBy(() -> service.execute(userId, sameAccount, UUID.randomUUID(), "hash", "127.0.0.1"))
                .isInstanceOf(DestinationAccountIneligibleException.class);
    }

    @Test
    void rejectsInsufficientFundsAndNeverCallsLedgerOrSavesATransfer() {
        when(accountService.getOwnedAccountOrThrow(userId, source.getId())).thenReturn(source);
        when(accountService.getAccountOrThrow(destination.getId())).thenReturn(destination);
        when(accountService.lockAccountsForUpdate(any(), any())).thenReturn(List.of(source, destination));

        assertThatThrownBy(() -> service.execute(userId, request(999_999L), UUID.randomUUID(), "hash", "127.0.0.1"))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(source.getBalanceCents()).isEqualTo(10_000L); // untouched
        verifyNoInteractions(ledgerService);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void successfulTransferDebitsCreditsRecordsLedgerAndPersistsIdempotencyRecord() {
        when(accountService.getOwnedAccountOrThrow(userId, source.getId())).thenReturn(source);
        when(accountService.getAccountOrThrow(destination.getId())).thenReturn(destination);
        when(accountService.lockAccountsForUpdate(any(), any())).thenReturn(List.of(source, destination));

        UUID idempotencyKey = UUID.randomUUID();
        TransferConfirmationDto result = service.execute(userId, request(2_500L), idempotencyKey, "hash", "127.0.0.1");

        assertThat(source.getBalanceCents()).isEqualTo(7_500L);
        assertThat(destination.getBalanceCents()).isEqualTo(2_500L);
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.amountCents()).isEqualTo(2_500L);
        assertThat(result.sourceAccount().balanceCents()).isEqualTo(7_500L);
        assertThat(result.destinationAccount().balanceCents()).isEqualTo(2_500L);

        verify(ledgerService).recordTransferEntries(any(), eq(source.getId()), eq(7_500L), eq(destination.getId()), eq(2_500L), eq(2_500L));
        verify(idempotencyRecordRepository).save(argThat(record ->
                record.getIdempotencyKey().equals(idempotencyKey) && record.getResponseStatus() == 201));
        verify(auditService).record(eq(userId), eq("TRANSFER_COMPLETED"), eq("TRANSFER"), any(), anyMap(), eq("127.0.0.1"));
    }

    @Test
    void rejectedTransferIsStillAudited() {
        when(accountService.getOwnedAccountOrThrow(userId, source.getId())).thenReturn(source);
        when(accountService.getAccountOrThrow(destination.getId())).thenReturn(destination);
        when(accountService.lockAccountsForUpdate(any(), any())).thenReturn(List.of(source, destination));

        assertThatThrownBy(() -> service.execute(userId, request(999_999L), UUID.randomUUID(), "hash", "127.0.0.1"))
                .isInstanceOf(InsufficientFundsException.class);

        verify(auditService).record(eq(userId), eq("TRANSFER_REJECTED"), eq("TRANSFER"), isNull(), anyMap(), eq("127.0.0.1"));
    }
}
