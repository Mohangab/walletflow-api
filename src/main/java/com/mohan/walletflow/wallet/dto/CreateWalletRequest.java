package com.mohan.walletflow.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateWalletRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO 4217 code") String currency) {
}
