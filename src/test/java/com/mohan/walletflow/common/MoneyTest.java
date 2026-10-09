package com.mohan.walletflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.money.Money;

class MoneyTest {

    @Test
    void normalisesToScaleTwo() {
        assertThat(Money.of(new BigDecimal("10"))).isEqualByComparingTo("10.00").hasScaleOf(2);
        assertThat(Money.of(new BigDecimal("10.5")).toPlainString()).isEqualTo("10.50");
    }

    @Test
    void rejectsMoreThanTwoDecimalsInsteadOfRounding() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.005"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void positiveRejectsZeroAndNegative() {
        assertThatThrownBy(() -> Money.positive(BigDecimal.ZERO)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Money.positive(new BigDecimal("-1"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Money.positive(null)).isInstanceOf(BusinessRuleException.class);
    }
}
