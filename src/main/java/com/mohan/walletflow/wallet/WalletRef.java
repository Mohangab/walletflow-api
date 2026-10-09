package com.mohan.walletflow.wallet;

import java.util.UUID;

/**
 * Read-only view of a wallet's immutable attributes. Used for checks performed <em>before</em> taking
 * row locks: loading the full entity first would put a possibly stale balance into the persistence
 * context, which a later SELECT ... FOR UPDATE would not refresh.
 */
public record WalletRef(UUID id, UUID ownerId, String currency) {

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }
}
