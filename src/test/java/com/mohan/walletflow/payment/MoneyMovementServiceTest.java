package com.mohan.walletflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.error.NotFoundException;
import com.mohan.walletflow.common.tx.TransactionRunner;
import com.mohan.walletflow.ledger.LedgerEntryRepository;
import com.mohan.walletflow.ledger.LedgerService;
import com.mohan.walletflow.ledger.LedgerTransactionRepository;
import com.mohan.walletflow.observability.PaymentMetrics;
import com.mohan.walletflow.payment.dto.TransactionResponse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.mohan.walletflow.payment.dto.TransferRequest;
import com.mohan.walletflow.security.AuthenticatedUser;
import com.mohan.walletflow.user.Role;
import com.mohan.walletflow.user.UserRepository;
import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletLocker;
import com.mohan.walletflow.wallet.WalletRef;
import com.mohan.walletflow.wallet.WalletRepository;

@ExtendWith(MockitoExtension.class)
class MoneyMovementServiceTest {

    @Mock
    private TransactionRunner txRunner;
    @Mock
    private WalletLocker locker;
    @Mock
    private WalletRepository wallets;
    @Mock
    private UserRepository users;
    @Mock
    private LedgerTransactionRepository ledgerTransactions;
    @Mock
    private LedgerEntryRepository ledgerEntries;

    private MoneyMovementService service;
    private final AuthenticatedUser alice = new AuthenticatedUser(UUID.randomUUID(), "alice@x.io", Role.USER);
    private Wallet aliceInr;
    private Wallet bobInr;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        LedgerService ledger = new LedgerService(ledgerTransactions, ledgerEntries,
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
        service = new MoneyMovementService(txRunner, locker, wallets, users, ledger, null,
                new PaymentMetrics(new SimpleMeterRegistry()));
        lenient().when(txRunner.inTransactionWithRetry(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        lenient().when(ledgerTransactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aliceInr = Wallet.openFor(alice.id(), "INR");
        aliceInr.credit(new BigDecimal("100.00"));
        bobInr = Wallet.openFor(UUID.randomUUID(), "INR");
        lenient().when(wallets.findRefById(aliceInr.getId()))
                .thenReturn(Optional.of(new WalletRef(aliceInr.getId(), alice.id(), "INR")));
    }

    private void lockReturns(Wallet... locked) {
        Map<UUID, Wallet> map = new LinkedHashMap<>();
        for (Wallet w : locked) {
            map.put(w.getId(), w);
        }
        when(locker.lockInOrder(any(UUID[].class))).thenReturn(map);
    }

    @Test
    void transferMovesMoneyAndReportsSourceBalance() {
        lockReturns(aliceInr, bobInr);

        TransactionResponse response = service.transfer(alice,
                new TransferRequest(aliceInr.getId(), bobInr.getId(), null, new BigDecimal("40.00"), "dinner"));

        assertThat(response.walletId()).isEqualTo(aliceInr.getId());
        assertThat(response.walletBalanceAfter()).isEqualByComparingTo("60.00");
        assertThat(aliceInr.getBalance()).isEqualByComparingTo("60.00");
        assertThat(bobInr.getBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    void transferToSameWalletIsRejectedBeforeLocking() {
        assertThatThrownBy(() -> service.transfer(alice,
                new TransferRequest(aliceInr.getId(), aliceInr.getId(), null, BigDecimal.TEN, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("differ");
        verify(locker, never()).lockInOrder(any(UUID[].class));
    }

    @Test
    void transferFromSomeoneElsesWalletIsNotFound() {
        AuthenticatedUser mallory = new AuthenticatedUser(UUID.randomUUID(), "m@x.io", Role.USER);

        assertThatThrownBy(() -> service.transfer(mallory,
                new TransferRequest(aliceInr.getId(), bobInr.getId(), null, BigDecimal.TEN, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void currencyMismatchIsRejected() {
        Wallet bobUsd = Wallet.openFor(UUID.randomUUID(), "USD");
        lockReturns(aliceInr, bobUsd);

        assertThatThrownBy(() -> service.transfer(alice,
                new TransferRequest(aliceInr.getId(), bobUsd.getId(), null, BigDecimal.TEN, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("currency-mismatch");
        assertThat(aliceInr.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void recipientEmailResolvesToWalletInSourceCurrency() {
        var bob = new com.mohan.walletflow.user.User("bob@x.io", "h", "Bob", Role.USER);
        when(users.findByEmailIgnoreCase("bob@x.io")).thenReturn(Optional.of(bob));
        when(wallets.findIdByOwnerIdAndCurrency(bob.getId(), "INR")).thenReturn(Optional.of(bobInr.getId()));
        lockReturns(aliceInr, bobInr);

        service.transfer(alice, new TransferRequest(aliceInr.getId(), null, "bob@x.io", new BigDecimal("1.50"), null));

        verify(locker).lockInOrder(eq(aliceInr.getId()), eq(bobInr.getId()));
        assertThat(bobInr.getBalance()).isEqualByComparingTo("1.50");
    }
}
