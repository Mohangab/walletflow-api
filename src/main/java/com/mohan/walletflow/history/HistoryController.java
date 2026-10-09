package com.mohan.walletflow.history;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mohan.walletflow.common.web.PageResponse;
import com.mohan.walletflow.history.dto.EntryResponse;
import com.mohan.walletflow.history.dto.StatementResponse;
import com.mohan.walletflow.history.dto.TransactionView;
import com.mohan.walletflow.ledger.EntryDirection;
import com.mohan.walletflow.ledger.TransactionType;
import com.mohan.walletflow.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "History", description = "Transaction history and statements, derived from the ledger")
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping("/wallets/{walletId}/transactions")
    @Operation(summary = "Paginated wallet history with optional filters (sort by createdAt or amount)")
    public PageResponse<EntryResponse> history(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID walletId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) EntryDirection direction,
            @Parameter(description = "Inclusive lower bound, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Exclusive upper bound, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return history.history(user, walletId, new HistoryFilter(type, direction, from, to), pageable);
    }

    @GetMapping("/wallets/{walletId}/statement")
    @Operation(summary = "Statement with opening/closing balance for an inclusive UTC date range")
    public StatementResponse statement(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID walletId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return history.statement(user, walletId, from, to);
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "Get a transaction I took part in")
    public TransactionView transaction(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID transactionId) {
        return history.transaction(user, transactionId);
    }
}
