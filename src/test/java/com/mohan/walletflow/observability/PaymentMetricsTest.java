package com.mohan.walletflow.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mohan.walletflow.ledger.TransactionType;
import com.mohan.walletflow.payment.dto.TransactionResponse;
import com.mohan.walletflow.wallet.InsufficientFundsException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PaymentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PaymentMetrics metrics = new PaymentMetrics(registry);

    private TransactionResponse response() {
        return new TransactionResponse(UUID.randomUUID(), TransactionType.TRANSFER, "COMPLETED", new BigDecimal("12.50"),
                "INR", UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), BigDecimal.ONE, Instant.now());
    }

    @Test
    void countsSuccessfulTransfersButNotReplays() {
        metrics.success(TransactionType.TRANSFER, response(), false, 1_000_000);
        metrics.success(TransactionType.TRANSFER, response(), true, 1_000);

        assertThat(registry.get("walletflow.transfers").tag("currency", "INR").counter().count()).isEqualTo(1);
        assertThat(registry.get("walletflow.idempotency.replays").counter().count()).isEqualTo(1);
        assertThat(registry.get("walletflow.transfer.amount").summary().totalAmount()).isEqualTo(12.5);
        assertThat(registry.get("walletflow.transfer.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void countsFailuresByReason() {
        metrics.failure(TransactionType.TRANSFER, new InsufficientFundsException());
        metrics.failure(TransactionType.DEPOSIT, new IllegalStateException("boom"));

        assertThat(registry.get("walletflow.transfer.failures").tag("reason", "insufficient-funds").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("walletflow.money.movements").tag("type", "DEPOSIT").tag("outcome", "failure")
                .counter().count()).isEqualTo(1);
    }
}
