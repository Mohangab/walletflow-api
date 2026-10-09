package com.mohan.walletflow.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mohan.walletflow.payment.dto.DepositRequest;
import com.mohan.walletflow.payment.dto.TransactionResponse;
import com.mohan.walletflow.payment.dto.TransferRequest;
import com.mohan.walletflow.payment.dto.WithdrawalRequest;
import com.mohan.walletflow.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments", description = "Deposits, withdrawals and peer-to-peer transfers")
public class PaymentController {

    private final MoneyMovementService payments;

    public PaymentController(MoneyMovementService payments) {
        this.payments = payments;
    }

    @PostMapping("/wallets/{walletId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposit funds into one of my wallets")
    public TransactionResponse deposit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request) {
        return payments.deposit(user, walletId, request);
    }

    @PostMapping("/wallets/{walletId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Withdraw funds from one of my wallets")
    public TransactionResponse withdraw(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID walletId,
            @Valid @RequestBody WithdrawalRequest request) {
        return payments.withdraw(user, walletId, request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer funds to another user's wallet (same currency)")
    public TransactionResponse transfer(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TransferRequest request) {
        return payments.transfer(user, request);
    }
}
