package com.mohan.walletflow.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /** Balance derived purely from the ledger: sum(credits) - sum(debits). */
    @Query("""
            select coalesce(sum(case when e.direction = com.mohan.walletflow.ledger.EntryDirection.CREDIT
                                     then e.amount else -e.amount end), 0)
            from LedgerEntry e where e.walletId = :walletId""")
    BigDecimal derivedBalance(@Param("walletId") UUID walletId);
}
