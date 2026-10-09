package com.mohan.walletflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mohan.walletflow.ledger.LedgerTransaction;
import com.mohan.walletflow.ledger.TransactionType;

/**
 * Result of a money movement. {@code walletBalanceAfter} is the balance of the caller's wallet
 * right after the posting; the counterparty's balance is never exposed.
 */
public record TransactionResponse(UUID id, TransactionType type, String status, BigDecimal amount, String currency,
        UUID sourceWalletId, UUID destinationWalletId, String description, UUID walletId,
        BigDecimal walletBalanceAfter, Instant createdAt) {

    public static TransactionResponse from(LedgerTransaction tx, UUID walletId, BigDecimal walletBalanceAfter) {
        return new TransactionResponse(tx.getId(), tx.getType(), tx.getStatus(), tx.getAmount(), tx.getCurrency(),
                tx.getSourceWalletId(), tx.getDestinationWalletId(), tx.getDescription(), walletId,
                walletBalanceAfter, tx.getCreatedAt());
    }
}
