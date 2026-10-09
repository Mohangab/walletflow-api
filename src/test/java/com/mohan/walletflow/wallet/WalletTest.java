package com.mohan.walletflow.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mohan.walletflow.common.error.BusinessRuleException;

class WalletTest {

    private final Wallet wallet = Wallet.openFor(UUID.randomUUID(), "INR");

    @Test
    void creditAndDebitAdjustBalance() {
        wallet.credit(new BigDecimal("100.00"));
        wallet.debit(new BigDecimal("40.25"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("59.75");
    }

    @Test
    void userWalletCannotGoNegative() {
        wallet.credit(new BigDecimal("10.00"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("10.01"))).isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void frozenWalletRejectsMovements() {
        wallet.freeze();

        assertThatThrownBy(() -> wallet.credit(BigDecimal.ONE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("frozen");
    }
}
