package com.mohan.walletflow.admin;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.error.NotFoundException;
import com.mohan.walletflow.common.tx.TransactionRunner;
import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletKind;
import com.mohan.walletflow.wallet.WalletRepository;
import com.mohan.walletflow.wallet.dto.WalletResponse;

/**
 * Freeze/unfreeze relies on optimistic locking only: no row lock is taken, and if a transfer updates the
 * wallet concurrently the @Version check fails and {@link TransactionRunner} retries on fresh state.
 */
@Service
public class AdminWalletService {

    private final WalletRepository wallets;
    private final TransactionRunner txRunner;

    public AdminWalletService(WalletRepository wallets, TransactionRunner txRunner) {
        this.wallets = wallets;
        this.txRunner = txRunner;
    }

    public WalletResponse freeze(UUID walletId) {
        return txRunner.inTransactionWithRetry(() -> {
            Wallet wallet = userWallet(walletId);
            wallet.freeze();
            return WalletResponse.from(wallets.saveAndFlush(wallet));
        });
    }

    public WalletResponse unfreeze(UUID walletId) {
        return txRunner.inTransactionWithRetry(() -> {
            Wallet wallet = userWallet(walletId);
            wallet.unfreeze();
            return WalletResponse.from(wallets.saveAndFlush(wallet));
        });
    }

    private Wallet userWallet(UUID walletId) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new NotFoundException("Wallet", walletId));
        if (wallet.getKind() == WalletKind.SYSTEM) {
            throw new BusinessRuleException("system-wallet", "Settlement wallets cannot be frozen");
        }
        return wallet;
    }
}
