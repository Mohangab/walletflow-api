package com.mohan.walletflow.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WithdrawalRequest(
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Size(max = 255) String description) {
}
