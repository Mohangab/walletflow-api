package com.mohan.walletflow.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.money.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletKind kind;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    /** Optimistic lock: any concurrent update that slipped past the row lock fails instead of losing an update. */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    private Wallet(UUID ownerId, WalletKind kind, String currency) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.kind = kind;
        this.currency = currency;
        this.balance = Money.ZERO;
        this.status = WalletStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Wallet openFor(UUID ownerId, String currency) {
        return new Wallet(ownerId, WalletKind.USER, currency);
    }

    /** Increase the balance. Only called by the ledger while the row is locked. */
    public void credit(BigDecimal amount) {
        requireActive();
        balance = balance.add(Money.positive(amount));
        updatedAt = Instant.now();
    }

    /** Decrease the balance. USER wallets may never go below zero. */
    public void debit(BigDecimal amount) {
        requireActive();
        BigDecimal next = balance.subtract(Money.positive(amount));
        if (kind == WalletKind.USER && next.signum() < 0) {
            throw new InsufficientFundsException();
        }
        balance = next;
        updatedAt = Instant.now();
    }

    public void freeze() {
        status = WalletStatus.FROZEN;
        updatedAt = Instant.now();
    }

    public void unfreeze() {
        status = WalletStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    private void requireActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new BusinessRuleException("wallet-frozen", "Wallet " + id + " is frozen");
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public WalletKind getKind() {
        return kind;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
