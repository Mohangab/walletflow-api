package com.mohan.walletflow.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.mohan.walletflow.admin.dto.ReconciliationReport;
import com.mohan.walletflow.admin.dto.ReconciliationReport.CurrencyTotals;
import com.mohan.walletflow.admin.dto.ReconciliationReport.WalletMismatch;
import com.mohan.walletflow.common.money.Money;
import com.mohan.walletflow.ledger.LedgerEntryRepository;
import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletRepository;

/**
 * Verifies the ledger invariants:
 * <ol>
 *   <li>every wallet's stored balance equals its ledger-derived balance;</li>
 *   <li>every journal is balanced (debits == credits);</li>
 *   <li>per currency, total debits == total credits (trial balance).</li>
 * </ol>
 * Runs in a REPEATABLE READ read-only transaction so all queries see one consistent snapshot even while
 * transfers keep committing.
 */
@Service
public class ReconciliationService {

    private final WalletRepository wallets;
    private final LedgerEntryRepository entries;
    private final Clock clock;

    public ReconciliationService(WalletRepository wallets, LedgerEntryRepository entries, Clock clock) {
        this.wallets = wallets;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport reconcile() {
        Map<UUID, BigDecimal> derived = new HashMap<>();
        for (Object[] row : entries.derivedBalancesByWallet()) {
            derived.put((UUID) row[0], (BigDecimal) row[1]);
        }

        List<Wallet> all = wallets.findAll();
        List<WalletMismatch> mismatches = new ArrayList<>();
        for (Wallet wallet : all) {
            BigDecimal ledger = Money.of(derived.getOrDefault(wallet.getId(), BigDecimal.ZERO));
            if (ledger.compareTo(wallet.getBalance()) != 0) {
                mismatches.add(new WalletMismatch(wallet.getId(), wallet.getCurrency(), wallet.getBalance(), ledger,
                        wallet.getBalance().subtract(ledger)));
            }
        }

        List<UUID> unbalanced = entries.findUnbalancedTransactionIds();

        List<CurrencyTotals> trial = new ArrayList<>();
        for (Object[] row : entries.trialBalance()) {
            BigDecimal debits = Money.of((BigDecimal) row[1]);
            BigDecimal credits = Money.of((BigDecimal) row[2]);
            trial.add(new CurrencyTotals((String) row[0], debits, credits, debits.compareTo(credits) == 0));
        }

        boolean healthy = mismatches.isEmpty() && unbalanced.isEmpty()
                && trial.stream().allMatch(CurrencyTotals::balanced);
        return new ReconciliationReport(clock.instant(), healthy, all.size(), mismatches, unbalanced, trial);
    }
}
