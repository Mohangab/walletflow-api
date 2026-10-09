package com.mohan.walletflow.wallet;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.error.ConflictException;
import com.mohan.walletflow.common.error.NotFoundException;
import com.mohan.walletflow.security.AuthenticatedUser;
import com.mohan.walletflow.wallet.dto.WalletResponse;

@Service
public class WalletService {

    private final WalletRepository wallets;

    public WalletService(WalletRepository wallets) {
        this.wallets = wallets;
    }

    @Transactional
    public WalletResponse open(UUID ownerId, String requestedCurrency) {
        String currency = requireSupported(requestedCurrency);
        if (wallets.existsByOwnerIdAndCurrency(ownerId, currency)) {
            throw new ConflictException("wallet-exists", "You already have a " + currency + " wallet");
        }
        try {
            return WalletResponse.from(wallets.saveAndFlush(Wallet.openFor(ownerId, currency)));
        } catch (DataIntegrityViolationException race) {
            // Two concurrent "open" calls: the unique (owner_id, currency) constraint picked a winner.
            throw new ConflictException("wallet-exists", "You already have a " + currency + " wallet");
        }
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> listFor(UUID ownerId) {
        return wallets.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(WalletResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WalletResponse get(AuthenticatedUser user, UUID walletId) {
        return WalletResponse.from(requireReadable(user, walletId));
    }

    /** Owners can read their wallets, admins can read any. Others get 404 so wallet ids cannot be probed. */
    @Transactional(readOnly = true)
    public Wallet requireReadable(AuthenticatedUser user, UUID walletId) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new NotFoundException("Wallet", walletId));
        if (!wallet.isOwnedBy(user.id()) && !user.isAdmin()) {
            throw new NotFoundException("Wallet", walletId);
        }
        return wallet;
    }

    public String requireSupported(String requestedCurrency) {
        String currency = requestedCurrency == null ? "" : requestedCurrency.trim().toUpperCase(Locale.ROOT);
        if (wallets.findSystemWallet(currency).isEmpty()) {
            throw new BusinessRuleException("unsupported-currency",
                    "Currency " + currency + " is not supported. Supported: " + wallets.findSupportedCurrencies());
        }
        return currency;
    }
}
