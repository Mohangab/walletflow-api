package com.mohan.walletflow.observability;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mohan.walletflow.admin.ReconciliationService;
import com.mohan.walletflow.admin.dto.ReconciliationReport;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Periodically reconciles the ledger and publishes the result as gauges, so Grafana/alerting can page
 * on {@code walletflow_reconciliation_mismatches > 0} without anyone calling the admin endpoint.
 */
@Component
public class ReconciliationMonitor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationMonitor.class);

    private final ReconciliationService reconciliation;
    private final AtomicInteger mismatches = new AtomicInteger();
    private final AtomicInteger unbalanced = new AtomicInteger();
    private final AtomicInteger healthy = new AtomicInteger(1);

    public ReconciliationMonitor(ReconciliationService reconciliation, MeterRegistry registry) {
        this.reconciliation = reconciliation;
        Gauge.builder("walletflow.reconciliation.mismatches", mismatches, AtomicInteger::get)
                .description("Wallets whose stored balance differs from the ledger").register(registry);
        Gauge.builder("walletflow.reconciliation.unbalanced.transactions", unbalanced, AtomicInteger::get)
                .description("Journals whose debits != credits").register(registry);
        Gauge.builder("walletflow.reconciliation.healthy", healthy, AtomicInteger::get)
                .description("1 if the last reconciliation passed, else 0").register(registry);
    }

    @Scheduled(fixedDelayString = "${app.reconciliation.interval:PT5M}", initialDelayString = "PT30S")
    public void run() {
        ReconciliationReport report = reconciliation.reconcile();
        mismatches.set(report.mismatches().size());
        unbalanced.set(report.unbalancedTransactions().size());
        healthy.set(report.healthy() ? 1 : 0);
        if (!report.healthy()) {
            log.error("Ledger reconciliation FAILED: {} mismatched wallets, {} unbalanced journals",
                    report.mismatches().size(), report.unbalancedTransactions().size());
        }
    }
}
