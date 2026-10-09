package com.mohan.walletflow.observability;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.mohan.walletflow.common.error.ApiException;
import com.mohan.walletflow.ledger.TransactionType;
import com.mohan.walletflow.payment.dto.TransactionResponse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Business metrics, exported to Prometheus as e.g.
 * {@code walletflow_transfers_total}, {@code walletflow_transfer_failures_total{reason=...}},
 * {@code walletflow_transfer_duration_seconds}, {@code walletflow_money_movements_total{type,outcome}}.
 */
@Component
public class PaymentMetrics {

    private final MeterRegistry registry;
    private final Counter idempotentReplays;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.idempotentReplays = Counter.builder("walletflow.idempotency.replays")
                .description("Requests answered from a stored idempotent response")
                .register(registry);
    }

    public void success(TransactionType type, TransactionResponse response, boolean replayed, long elapsedNanos) {
        if (replayed) {
            idempotentReplays.increment();
            return;
        }
        movement(type, "success").increment();
        if (type == TransactionType.TRANSFER) {
            Counter.builder("walletflow.transfers")
                    .description("Completed peer-to-peer transfers")
                    .tag("currency", response.currency())
                    .register(registry).increment();
            DistributionSummary.builder("walletflow.transfer.amount")
                    .description("Transferred amounts")
                    .tag("currency", response.currency())
                    .register(registry).record(response.amount().doubleValue());
            Timer.builder("walletflow.transfer.duration")
                    .description("End-to-end transfer processing time (locking, ledger posting, commit)")
                    .register(registry).record(elapsedNanos, TimeUnit.NANOSECONDS);
        }
    }

    public void failure(TransactionType type, Throwable error) {
        String reason = error instanceof ApiException api ? api.getCode() : error.getClass().getSimpleName();
        movement(type, "failure").increment();
        if (type == TransactionType.TRANSFER) {
            Counter.builder("walletflow.transfer.failures")
                    .description("Rejected or failed transfers by reason")
                    .tag("reason", reason)
                    .register(registry).increment();
        }
    }

    private Counter movement(TransactionType type, String outcome) {
        return Counter.builder("walletflow.money.movements")
                .description("Deposits, withdrawals and transfers by outcome")
                .tag("type", type.name())
                .tag("outcome", outcome)
                .register(registry);
    }
}
