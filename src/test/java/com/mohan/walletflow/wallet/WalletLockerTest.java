package com.mohan.walletflow.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletLockerTest {

    @Mock
    private WalletRepository repository;

    @Test
    void locksInAscendingIdOrderRegardlessOfArgumentOrder() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID high = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        when(repository.findByIdForUpdate(any())).thenAnswer(inv -> Optional.of(Wallet.openFor(UUID.randomUUID(), "INR")));

        var locked = new WalletLocker(repository).lockInOrder(high, low);

        InOrder order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(low);
        order.verify(repository).findByIdForUpdate(high);
        assertThat(locked).containsOnlyKeys(low, high);
    }
}
