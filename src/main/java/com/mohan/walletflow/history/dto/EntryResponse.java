package com.mohan.walletflow.history.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mohan.walletflow.ledger.EntryDirection;
import com.mohan.walletflow.ledger.LedgerEntry;
import com.mohan.walletflow.ledger.LedgerTransaction;
import com.mohan.walletflow.ledger.TransactionType;

/** One line of a wallet's history, seen from that wallet's perspective. */
public record EntryResponse(UUID entryId, UUID transactionId, TransactionType type, EntryDirection direction,
        BigDecimal amount, String currency, BigDecimal balanceAfter, UUID counterpartyWalletId, String description,
        Instant createdAt) {

    public static EntryResponse from(LedgerEntry entry) {
        LedgerTransaction tx = entry.getTransaction();
        UUID counterparty = null;
        if (tx.getType() == TransactionType.TRANSFER) {
            counterparty = entry.getWalletId().equals(tx.getSourceWalletId()) ? tx.getDestinationWalletId()
                    : tx.getSourceWalletId();
        }
        return new EntryResponse(entry.getId(), tx.getId(), tx.getType(), entry.getDirection(), entry.getAmount(),
                entry.getCurrency(), entry.getBalanceAfter(), counterparty, tx.getDescription(), entry.getCreatedAt());
    }
}
