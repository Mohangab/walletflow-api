package com.mohan.walletflow.history.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Account statement for an inclusive UTC date range; every figure is derived from the ledger. */
public record StatementResponse(UUID walletId, String currency, LocalDate from, LocalDate to,
        BigDecimal openingBalance, BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal closingBalance,
        int entryCount, List<EntryResponse> entries) {
}
