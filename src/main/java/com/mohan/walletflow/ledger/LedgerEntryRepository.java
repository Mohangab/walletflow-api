package com.mohan.walletflow.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {

    String SIGNED_AMOUNT = "case when e.direction = com.mohan.walletflow.ledger.EntryDirection.CREDIT "
            + "then e.amount else -e.amount end";

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    @Override
    @EntityGraph(attributePaths = "transaction")
    Page<LedgerEntry> findAll(Specification<LedgerEntry> spec, Pageable pageable);

    @EntityGraph(attributePaths = "transaction")
    @Query("""
            select e from LedgerEntry e
            where e.walletId = :walletId and e.createdAt >= :from and e.createdAt < :to
            order by e.createdAt asc, e.id asc""")
    List<LedgerEntry> findForStatement(@Param("walletId") UUID walletId, @Param("from") Instant from,
            @Param("to") Instant to);

    /** Balance derived purely from the ledger: sum(credits) - sum(debits). */
    @Query("select coalesce(sum(" + SIGNED_AMOUNT + "), 0) from LedgerEntry e where e.walletId = :walletId")
    BigDecimal derivedBalance(@Param("walletId") UUID walletId);

    @Query("select coalesce(sum(" + SIGNED_AMOUNT + "), 0) from LedgerEntry e "
            + "where e.walletId = :walletId and e.createdAt < :before")
    BigDecimal derivedBalanceBefore(@Param("walletId") UUID walletId, @Param("before") Instant before);

    /** Ledger-derived balance of every wallet that has entries: rows of [walletId, balance]. */
    @Query("select e.walletId, sum(" + SIGNED_AMOUNT + ") from LedgerEntry e group by e.walletId")
    List<Object[]> derivedBalancesByWallet();

    /** Transactions whose debit and credit legs do not sum to the same amount (must always be empty). */
    @Query("""
            select e.transaction.id from LedgerEntry e group by e.transaction.id
            having sum(case when e.direction = com.mohan.walletflow.ledger.EntryDirection.DEBIT then e.amount else 0 end)
                <> sum(case when e.direction = com.mohan.walletflow.ledger.EntryDirection.CREDIT then e.amount else 0 end)""")
    List<UUID> findUnbalancedTransactionIds();

    /** Trial balance per currency: rows of [currency, totalDebits, totalCredits]. */
    @Query("""
            select e.currency,
                   sum(case when e.direction = com.mohan.walletflow.ledger.EntryDirection.DEBIT then e.amount else 0 end),
                   sum(case when e.direction = com.mohan.walletflow.ledger.EntryDirection.CREDIT then e.amount else 0 end)
            from LedgerEntry e group by e.currency order by e.currency""")
    List<Object[]> trialBalance();
}
