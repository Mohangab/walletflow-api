package com.mohan.walletflow.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletStatus;

public record WalletResponse(UUID id, UUID ownerId, String currency, BigDecimal balance, WalletStatus status,
        Instant createdAt, Instant updatedAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getCurrency(), wallet.getBalance(),
                wallet.getStatus(), wallet.getCreatedAt(), wallet.getUpdatedAt());
    }
}
