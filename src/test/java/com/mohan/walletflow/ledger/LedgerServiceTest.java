package com.mohan.walletflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mohan.walletflow.ledger.LedgerService.Leg;
import com.mohan.walletflow.ledger.LedgerService.Posting;
import com.mohan.walletflow.wallet.InsufficientFundsException;
import com.mohan.walletflow.wallet.Wallet;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerTransactionRepository transactions;
    @Mock
    private LedgerEntryRepository entries;

    private LedgerService ledger;
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ledger = new LedgerService(transactions, entries,
                Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void moveWritesBalancedDebitAndCreditEntries() {
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Wallet from = Wallet.openFor(UUID.randomUUID(), "INR");
        Wallet to = Wallet.openFor(UUID.randomUUID(), "INR");
        from.credit(new BigDecimal("100.00"));

        Posting posting = ledger.move(TransactionType.TRANSFER, from, to, new BigDecimal("30.00"), "rent", actor);

        assertThat(posting.entries()).hasSize(2);
        assertThat(posting.entries().stream().map(LedgerEntry::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0");
        assertThat(posting.entryFor(from).getDirection()).isEqualTo(EntryDirection.DEBIT);
        assertThat(posting.entryFor(from).getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(posting.entryFor(to).getDirection()).isEqualTo(EntryDirection.CREDIT);
        assertThat(posting.entryFor(to).getBalanceAfter()).isEqualByComparingTo("30.00");
        assertThat(posting.transaction().getAmount()).isEqualByComparingTo("30.00");
        verify(entries).saveAll(posting.entries());
    }

    @Test
    void unbalancedJournalIsRejected() {
        Wallet a = Wallet.openFor(UUID.randomUUID(), "INR");
        Wallet b = Wallet.openFor(UUID.randomUUID(), "INR");

        assertThatThrownBy(() -> ledger.post(TransactionType.TRANSFER, a, b, null, actor,
                List.of(Leg.debit(a, new BigDecimal("10.00")), Leg.credit(b, new BigDecimal("9.99")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unbalanced");
        verify(transactions, never()).save(any());
    }

    @Test
    void insufficientFundsAbortsBeforeEntriesAreSaved() {
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Wallet from = Wallet.openFor(UUID.randomUUID(), "USD");
        Wallet to = Wallet.openFor(UUID.randomUUID(), "USD");

        assertThatThrownBy(() -> ledger.move(TransactionType.TRANSFER, from, to, new BigDecimal("1.00"), null, actor))
                .isInstanceOf(InsufficientFundsException.class);
        verify(entries, never()).saveAll(any());
    }

    @Test
    void mixedCurrencyJournalIsRejected() {
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Wallet inr = Wallet.openFor(UUID.randomUUID(), "INR");
        Wallet usd = Wallet.openFor(UUID.randomUUID(), "USD");
        inr.credit(new BigDecimal("5.00"));

        assertThatThrownBy(() -> ledger.move(TransactionType.TRANSFER, inr, usd, new BigDecimal("1.00"), null, actor))
                .isInstanceOf(IllegalStateException.class);
    }
}
