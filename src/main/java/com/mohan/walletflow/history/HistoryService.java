package com.mohan.walletflow.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mohan.walletflow.common.error.ApiException;
import com.mohan.walletflow.common.error.NotFoundException;
import com.mohan.walletflow.common.money.Money;
import com.mohan.walletflow.common.web.PageResponse;
import com.mohan.walletflow.history.dto.EntryResponse;
import com.mohan.walletflow.history.dto.StatementResponse;
import com.mohan.walletflow.history.dto.TransactionView;
import com.mohan.walletflow.ledger.EntryDirection;
import com.mohan.walletflow.ledger.LedgerEntry;
import com.mohan.walletflow.ledger.LedgerEntryRepository;
import com.mohan.walletflow.ledger.LedgerTransaction;
import com.mohan.walletflow.ledger.LedgerTransactionRepository;
import com.mohan.walletflow.security.AuthenticatedUser;
import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletRepository;
import com.mohan.walletflow.wallet.WalletService;

@Service
@Transactional(readOnly = true)
public class HistoryService {

    static final int MAX_STATEMENT_DAYS = 366;
    private static final Set<String> SORTABLE = Set.of("createdAt", "amount");

    private final WalletService walletService;
    private final WalletRepository wallets;
    private final LedgerEntryRepository entries;
    private final LedgerTransactionRepository transactions;

    public HistoryService(WalletService walletService, WalletRepository wallets, LedgerEntryRepository entries,
            LedgerTransactionRepository transactions) {
        this.walletService = walletService;
        this.wallets = wallets;
        this.entries = entries;
        this.transactions = transactions;
    }

    public PageResponse<EntryResponse> history(AuthenticatedUser user, UUID walletId, HistoryFilter filter,
            Pageable pageable) {
        walletService.requireReadable(user, walletId);
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw badRequest("'from' must be before 'to'");
        }
        return PageResponse.of(entries.findAll(filter.forWallet(walletId), withSafeSort(pageable)),
                EntryResponse::from);
    }

    public StatementResponse statement(AuthenticatedUser user, UUID walletId, LocalDate from, LocalDate to) {
        Wallet wallet = walletService.requireReadable(user, walletId);
        if (to.isBefore(from)) {
            throw badRequest("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_STATEMENT_DAYS) {
            throw badRequest("Statement range is limited to " + MAX_STATEMENT_DAYS + " days");
        }
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal opening = Money.of(entries.derivedBalanceBefore(walletId, start));
        List<LedgerEntry> lines = entries.findForStatement(walletId, start, end);
        BigDecimal credits = sum(lines, EntryDirection.CREDIT);
        BigDecimal debits = sum(lines, EntryDirection.DEBIT);
        BigDecimal closing = opening.add(credits).subtract(debits);

        return new StatementResponse(walletId, wallet.getCurrency(), from, to, opening, credits, debits, closing,
                lines.size(), lines.stream().map(EntryResponse::from).toList());
    }

    public TransactionView transaction(AuthenticatedUser user, UUID transactionId) {
        LedgerTransaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction", transactionId));
        if (!user.isAdmin() && !ownsEither(user.id(), tx)) {
            throw new NotFoundException("Transaction", transactionId);
        }
        return TransactionView.from(tx);
    }

    private boolean ownsEither(UUID userId, LedgerTransaction tx) {
        return wallets.findRefById(tx.getSourceWalletId()).map(w -> w.isOwnedBy(userId)).orElse(false)
                || wallets.findRefById(tx.getDestinationWalletId()).map(w -> w.isOwnedBy(userId)).orElse(false);
    }

    private static BigDecimal sum(List<LedgerEntry> lines, EntryDirection direction) {
        return lines.stream().filter(e -> e.getDirection() == direction).map(LedgerEntry::getAmount)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    /** Only whitelisted sort properties; newest first by default, with id as a deterministic tie-breaker. */
    private static Pageable withSafeSort(Pageable pageable) {
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw badRequest("Cannot sort by '" + order.getProperty() + "'. Allowed: " + SORTABLE);
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")));
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid-query", message);
    }
}
