package com.mohan.walletflow.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.mohan.walletflow.ledger.EntryDirection;
import com.mohan.walletflow.ledger.LedgerEntry;
import com.mohan.walletflow.ledger.TransactionType;

import jakarta.persistence.criteria.Predicate;

/** Optional filters for wallet history; null fields are ignored. {@code to} is exclusive. */
public record HistoryFilter(TransactionType type, EntryDirection direction, Instant from, Instant to) {

    Specification<LedgerEntry> forWallet(UUID walletId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("walletId"), walletId));
            if (type != null) {
                predicates.add(cb.equal(root.get("transaction").get("type"), type));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
