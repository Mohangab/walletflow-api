package com.mohan.walletflow.history.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mohan.walletflow.ledger.LedgerTransaction;
import com.mohan.walletflow.ledger.TransactionType;

public record TransactionView(UUID id, TransactionType type, String status, BigDecimal amount, String currency,
        UUID sourceWalletId, UUID destinationWalletId, String description, Instant createdAt) {

    public static TransactionView from(LedgerTransaction tx) {
        return new TransactionView(tx.getId(), tx.getType(), tx.getStatus(), tx.getAmount(), tx.getCurrency(),
                tx.getSourceWalletId(), tx.getDestinationWalletId(), tx.getDescription(), tx.getCreatedAt());
    }
}
