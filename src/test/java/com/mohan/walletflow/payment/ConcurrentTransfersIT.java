package com.mohan.walletflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mohan.walletflow.ledger.LedgerEntryRepository;
import com.mohan.walletflow.payment.dto.DepositRequest;
import com.mohan.walletflow.payment.dto.TransferRequest;
import com.mohan.walletflow.security.AuthenticatedUser;
import com.mohan.walletflow.support.IntegrationTestBase;
import com.mohan.walletflow.user.Role;
import com.mohan.walletflow.user.User;
import com.mohan.walletflow.user.UserRepository;
import com.mohan.walletflow.wallet.InsufficientFundsException;
import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletRepository;

/**
 * Hammers the transfer path from many threads at once and checks the invariants a payments
 * system must never break: money is conserved, no balance goes negative, every cached balance
 * equals the balance derived from the ledger, and opposite-direction transfers never deadlock.
 */
class ConcurrentTransfersIT extends IntegrationTestBase {

    private static final int WALLETS = 6;
    private static final int THREADS = 12;
    private static final BigDecimal INITIAL = new BigDecimal("1000.00");

    @Autowired
    private MoneyMovementService payments;
    @Autowired
    private UserRepository users;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private LedgerEntryRepository entries;

    private record Account(AuthenticatedUser user, UUID walletId) {
    }

    private List<Account> fundedAccounts(int count, String currency) {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = users.save(new User(uniqueEmail(), "{noop}x", "Load " + i, Role.USER));
            Wallet wallet = wallets.save(Wallet.openFor(user.getId(), currency));
            AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), Role.USER);
            payments.deposit(principal, wallet.getId(), new DepositRequest(INITIAL, "seed"));
            accounts.add(new Account(principal, wallet.getId()));
        }
        return accounts;
    }

    @Test
    void parallelRandomTransfersConserveMoneyAndNeverGoNegative() throws Exception {
        List<Account> accounts = fundedAccounts(WALLETS, "GBP");
        int transfers = 400;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        Map<String, Integer> unexpected = new ConcurrentHashMap<>();

        runConcurrently(transfers, i -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Account from = accounts.get(rnd.nextInt(WALLETS));
            Account to = accounts.get(rnd.nextInt(WALLETS));
            if (from == to) {
                to = accounts.get((accounts.indexOf(from) + 1) % WALLETS);
            }
            // Amounts large enough that some transfers must fail for lack of funds.
            BigDecimal amount = BigDecimal.valueOf(rnd.nextInt(1, 60000), 2);
            try {
                payments.transfer(from.user(), new TransferRequest(from.walletId(), to.walletId(), null, amount, null));
                succeeded.incrementAndGet();
            } catch (InsufficientFundsException expected) {
                insufficient.incrementAndGet();
            } catch (RuntimeException ex) {
                unexpected.merge(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 1, Integer::sum);
            }
        });

        assertThat(unexpected).as("unexpected failures").isEmpty();
        assertThat(succeeded.get() + insufficient.get()).isEqualTo(transfers);
        assertThat(succeeded.get()).isPositive();
        assertInvariants(accounts);
    }

    @Test
    void opposingTransfersBetweenTwoWalletsDoNotDeadlock() throws Exception {
        List<Account> pair = fundedAccounts(2, "GBP");
        Map<String, Integer> unexpected = new ConcurrentHashMap<>();

        runConcurrently(200, i -> {
            Account from = pair.get(i % 2);
            Account to = pair.get((i + 1) % 2);
            try {
                payments.transfer(from.user(),
                        new TransferRequest(from.walletId(), to.walletId(), null, new BigDecimal("7.00"), null));
            } catch (InsufficientFundsException ignored) {
                // possible but irrelevant here
            } catch (RuntimeException ex) {
                unexpected.merge(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 1, Integer::sum);
            }
        });

        assertThat(unexpected).as("deadlocks / lock timeouts / lost updates").isEmpty();
        assertInvariants(pair);
    }

    private void assertInvariants(List<Account> accounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (Account account : accounts) {
            Wallet wallet = wallets.findById(account.walletId()).orElseThrow();
            assertThat(wallet.getBalance()).as("non-negative").isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(entries.derivedBalance(wallet.getId())).as("ledger-derived balance")
                    .isEqualByComparingTo(wallet.getBalance());
            total = total.add(wallet.getBalance());
        }
        assertThat(total).as("money is conserved")
                .isEqualByComparingTo(INITIAL.multiply(BigDecimal.valueOf(accounts.size())));
    }

    private interface Task {
        void run(int i);
    }

    private static void runConcurrently(int count, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int n = i;
            futures.add(pool.submit(() -> {
                start.await();
                task.run(n);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(2, TimeUnit.MINUTES);
        }
        pool.shutdown();
    }
}
