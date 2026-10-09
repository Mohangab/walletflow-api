package com.mohan.walletflow.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param healthy               true when every check passed
 * @param walletsChecked        number of wallets compared against the ledger
 * @param mismatches            wallets whose stored balance differs from sum(credits) - sum(debits)
 * @param unbalancedTransactions journals whose debits != credits (must always be empty)
 * @param trialBalance          per-currency totals; debits must equal credits
 */
public record ReconciliationReport(Instant checkedAt, boolean healthy, int walletsChecked,
        List<WalletMismatch> mismatches, List<UUID> unbalancedTransactions, List<CurrencyTotals> trialBalance) {

    public record WalletMismatch(UUID walletId, String currency, BigDecimal storedBalance, BigDecimal ledgerBalance,
            BigDecimal difference) {
    }

    public record CurrencyTotals(String currency, BigDecimal totalDebits, BigDecimal totalCredits, boolean balanced) {
    }
}
