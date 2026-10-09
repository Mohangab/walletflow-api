package com.mohan.walletflow.wallet;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mohan.walletflow.common.error.NotFoundException;

/**
 * Acquires row locks (SELECT ... FOR UPDATE) on several wallets in ascending id order.
 * <p>
 * Because every code path locks wallets in the same global order, two transfers A→B and B→A
 * can never each hold one lock while waiting for the other, so they cannot deadlock.
 */
@Component
public class WalletLocker {

    private final WalletRepository wallets;

    public WalletLocker(WalletRepository wallets) {
        this.wallets = wallets;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Wallet> lockInOrder(UUID... walletIds) {
        UUID[] ordered = Arrays.stream(walletIds).distinct().sorted().toArray(UUID[]::new);
        Map<UUID, Wallet> locked = new LinkedHashMap<>();
        for (UUID id : ordered) {
            locked.put(id, wallets.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Wallet", id)));
        }
        return locked;
    }
}
