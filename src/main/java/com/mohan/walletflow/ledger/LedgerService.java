package com.mohan.walletflow.ledger;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mohan.walletflow.common.money.Money;
import com.mohan.walletflow.wallet.Wallet;

/**
 * The only component allowed to change wallet balances. Every posting is a balanced journal:
 * the sum of debit legs equals the sum of credit legs, and each leg updates the (already locked)
 * wallet and appends an immutable {@link LedgerEntry} carrying the resulting balance.
 */
@Service
public class LedgerService {

    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final Clock clock;

    public LedgerService(LedgerTransactionRepository transactions, LedgerEntryRepository entries, Clock clock) {
        this.transactions = transactions;
        this.entries = entries;
        this.clock = clock;
    }

    /** One leg of a journal. */
    public record Leg(Wallet wallet, EntryDirection direction, BigDecimal amount) {
        public static Leg debit(Wallet wallet, BigDecimal amount) {
            return new Leg(wallet, EntryDirection.DEBIT, amount);
        }

        public static Leg credit(Wallet wallet, BigDecimal amount) {
            return new Leg(wallet, EntryDirection.CREDIT, amount);
        }
    }

    public record Posting(LedgerTransaction transaction, List<LedgerEntry> entries) {
        public LedgerEntry entryFor(Wallet wallet) {
            return entries.stream().filter(e -> e.getWalletId().equals(wallet.getId())).findFirst().orElseThrow();
        }
    }

    /** Simple two-legged posting: money moves from {@code from} to {@code to}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Posting move(TransactionType type, Wallet from, Wallet to, BigDecimal amount, String description,
            UUID initiatedBy) {
        BigDecimal value = Money.positive(amount);
        return post(type, from, to, description, initiatedBy, List.of(Leg.debit(from, value), Leg.credit(to, value)));
    }

    /**
     * Posts a balanced journal. Must run inside the caller's transaction, after the caller has locked
     * every wallet involved (in a consistent order), so balances and entries commit atomically.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Posting post(TransactionType type, Wallet source, Wallet destination, String description,
            UUID initiatedBy, List<Leg> legs) {
        assertBalanced(legs);
        String currency = source.getCurrency();
        BigDecimal amount = legs.stream().filter(l -> l.direction() == EntryDirection.DEBIT)
                .map(l -> Money.positive(l.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = clock.instant();

        LedgerTransaction tx = transactions.save(new LedgerTransaction(type, amount, currency, source.getId(),
                destination.getId(), description, initiatedBy, now));

        List<LedgerEntry> written = new ArrayList<>(legs.size());
        for (Leg leg : legs) {
            Wallet wallet = leg.wallet();
            if (!wallet.getCurrency().equals(currency)) {
                throw new IllegalStateException("All legs of a journal must share one currency");
            }
            BigDecimal value = Money.positive(leg.amount());
            if (leg.direction() == EntryDirection.DEBIT) {
                wallet.debit(value);
            } else {
                wallet.credit(value);
            }
            written.add(new LedgerEntry(tx, wallet.getId(), leg.direction(), value, currency, wallet.getBalance(), now));
        }
        entries.saveAll(written);
        return new Posting(tx, written);
    }

    static void assertBalanced(List<Leg> legs) {
        if (legs.size() < 2) {
            throw new IllegalStateException("A journal needs at least two legs");
        }
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (Leg leg : legs) {
            BigDecimal value = Money.positive(leg.amount());
            if (leg.direction() == EntryDirection.DEBIT) {
                debits = debits.add(value);
            } else {
                credits = credits.add(value);
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException("Unbalanced journal: debits " + debits + " != credits " + credits);
        }
    }
}
