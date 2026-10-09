package com.mohan.walletflow.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Header of a balanced journal: one business event (deposit, withdrawal, transfer). */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "source_wallet_id", nullable = false)
    private UUID sourceWalletId;

    @Column(name = "destination_wallet_id", nullable = false)
    private UUID destinationWalletId;

    @Column
    private String description;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    LedgerTransaction(TransactionType type, BigDecimal amount, String currency, UUID sourceWalletId,
            UUID destinationWalletId, String description, UUID initiatedBy, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.status = STATUS_COMPLETED;
        this.amount = amount;
        this.currency = currency;
        this.sourceWalletId = sourceWalletId;
        this.destinationWalletId = destinationWalletId;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getSourceWalletId() {
        return sourceWalletId;
    }

    public UUID getDestinationWalletId() {
        return destinationWalletId;
    }

    public String getDescription() {
        return description;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
