package com.mohan.walletflow.wallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /** SELECT ... FOR UPDATE. Callers must lock multiple wallets in a consistent (sorted id) order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    @Query("select new com.mohan.walletflow.wallet.WalletRef(w.id, w.ownerId, w.currency) from Wallet w where w.id = :id")
    Optional<WalletRef> findRefById(@Param("id") UUID id);

    @Query("select w.id from Wallet w where w.ownerId = :ownerId and w.currency = :currency")
    Optional<UUID> findIdByOwnerIdAndCurrency(@Param("ownerId") UUID ownerId, @Param("currency") String currency);

    @Query("select w.id from Wallet w where w.kind = com.mohan.walletflow.wallet.WalletKind.SYSTEM and w.currency = :currency")
    Optional<UUID> findSystemWalletId(@Param("currency") String currency);

    List<Wallet> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    boolean existsByOwnerIdAndCurrency(UUID ownerId, String currency);

    @Query("select w from Wallet w where w.kind = com.mohan.walletflow.wallet.WalletKind.SYSTEM and w.currency = :currency")
    Optional<Wallet> findSystemWallet(@Param("currency") String currency);

    @Query("select w.currency from Wallet w where w.kind = com.mohan.walletflow.wallet.WalletKind.SYSTEM order by w.currency")
    List<String> findSupportedCurrencies();
}
