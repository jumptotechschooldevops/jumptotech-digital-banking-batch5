package com.jumptotech.banking.transfer;

import com.jumptotech.banking.auth.AuthenticatedPrincipal;
import com.jumptotech.banking.transfer.dto.CreateTransferRequest;
import com.jumptotech.banking.transfer.dto.TransferConfirmationDto;
import com.jumptotech.banking.transfer.dto.TransferDetailDto;
import com.jumptotech.banking.transfer.dto.TransferExecutionResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferConfirmationDto> createTransfer(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        TransferExecutionResult result = transferService.executeTransfer(
                principal.getUserId(), request, idempotencyKey, httpRequest.getRemoteAddr());
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.confirmation());
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferDetailDto> getTransfer(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.getTransfer(principal.getUserId(), transferId));
    }
}
