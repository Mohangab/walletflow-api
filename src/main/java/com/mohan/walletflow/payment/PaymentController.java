package com.mohan.walletflow.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mohan.walletflow.idempotency.IdempotencyService;
import com.mohan.walletflow.idempotency.IdempotentResult;
import com.mohan.walletflow.payment.dto.DepositRequest;
import com.mohan.walletflow.payment.dto.TransactionResponse;
import com.mohan.walletflow.payment.dto.TransferRequest;
import com.mohan.walletflow.payment.dto.WithdrawalRequest;
import com.mohan.walletflow.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments", description = "Deposits, withdrawals and peer-to-peer transfers. All require an Idempotency-Key header.")
public class PaymentController {

    private static final String KEY_DOC = "Unique key per logical operation (e.g. a UUID). Retrying with the same key "
            + "and body replays the original response; reusing it with a different body returns 422.";

    private final MoneyMovementService payments;

    public PaymentController(MoneyMovementService payments) {
        this.payments = payments;
    }

    @PostMapping("/wallets/{walletId}/deposits")
    @Operation(summary = "Deposit funds into one of my wallets")
    public ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID walletId,
            @Parameter(description = KEY_DOC, required = true) @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        return created(payments.deposit(user, walletId, request, idempotencyKey));
    }

    @PostMapping("/wallets/{walletId}/withdrawals")
    @Operation(summary = "Withdraw funds from one of my wallets")
    public ResponseEntity<TransactionResponse> withdraw(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID walletId,
            @Parameter(description = KEY_DOC, required = true) @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest request) {
        return created(payments.withdraw(user, walletId, request, idempotencyKey));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfer funds to another user's wallet (same currency). Rate limited per user.")
    public ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = KEY_DOC, required = true) @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return created(payments.transfer(user, request, idempotencyKey));
    }

    private static ResponseEntity<TransactionResponse> created(IdempotentResult<TransactionResponse> result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IdempotencyService.REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.body());
    }
}
