package com.mohan.walletflow.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mohan.walletflow.admin.dto.ReconciliationReport;
import com.mohan.walletflow.ledger.LedgerEntryRepository;
import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletRepository;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private WalletRepository wallets;
    @Mock
    private LedgerEntryRepository entries;

    private ReconciliationService service() {
        return new ReconciliationService(wallets, entries, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void healthyWhenStoredBalancesMatchLedgerAndJournalsBalance() {
        Wallet funded = Wallet.openFor(UUID.randomUUID(), "INR");
        funded.credit(new BigDecimal("25.00"));
        Wallet empty = Wallet.openFor(UUID.randomUUID(), "INR");
        when(wallets.findAll()).thenReturn(List.of(funded, empty));
        when(entries.derivedBalancesByWallet()).thenReturn(List.<Object[]>of(new Object[] { funded.getId(), new BigDecimal("25") }));
        when(entries.findUnbalancedTransactionIds()).thenReturn(List.of());
        when(entries.trialBalance()).thenReturn(List.<Object[]>of(new Object[] { "INR", new BigDecimal("25"), new BigDecimal("25") }));

        ReconciliationReport report = service().reconcile();

        assertThat(report.healthy()).isTrue();
        assertThat(report.walletsChecked()).isEqualTo(2);
        assertThat(report.trialBalance()).singleElement().extracting("balanced").isEqualTo(true);
    }

    @Test
    void reportsMismatchesAndUnbalancedJournals() {
        Wallet drifted = Wallet.openFor(UUID.randomUUID(), "USD");
        drifted.credit(new BigDecimal("12.00"));
        UUID badTx = UUID.randomUUID();
        when(wallets.findAll()).thenReturn(List.of(drifted));
        when(entries.derivedBalancesByWallet()).thenReturn(List.<Object[]>of(new Object[] { drifted.getId(), new BigDecimal("10.00") }));
        when(entries.findUnbalancedTransactionIds()).thenReturn(List.of(badTx));
        when(entries.trialBalance()).thenReturn(List.<Object[]>of(new Object[] { "USD", new BigDecimal("10"), new BigDecimal("12") }));

        ReconciliationReport report = service().reconcile();

        assertThat(report.healthy()).isFalse();
        assertThat(report.mismatches()).singleElement().satisfies(m -> {
            assertThat(m.walletId()).isEqualTo(drifted.getId());
            assertThat(m.difference()).isEqualByComparingTo("2.00");
        });
        assertThat(report.unbalancedTransactions()).containsExactly(badTx);
        assertThat(report.trialBalance().get(0).balanced()).isFalse();
    }
}
