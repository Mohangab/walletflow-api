package com.mohan.walletflow.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Peer-to-peer transfer. Identify the recipient either by wallet id or by email; with an email the
 * recipient's wallet in the source wallet's currency is used.
 */
public record TransferRequest(
        @NotNull UUID sourceWalletId,
        @Schema(description = "Recipient wallet id (or use recipientEmail)") UUID destinationWalletId,
        @Schema(description = "Recipient email (or use destinationWalletId)") @Email String recipientEmail,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Size(max = 255) String description) {

    @JsonIgnore
    @AssertTrue(message = "exactly one of destinationWalletId or recipientEmail is required")
    public boolean isRecipientSpecifiedOnce() {
        return (destinationWalletId == null) != (recipientEmail == null || recipientEmail.isBlank());
    }
}
