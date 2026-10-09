package com.mohan.walletflow.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.mohan.walletflow.common.error.BusinessRuleException;

/** Money helpers. All amounts are BigDecimal with scale 2; never double/float. */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /**
     * Normalises to scale 2 without rounding. "10", "10.5" and "10.50" are accepted;
     * "10.005" is rejected rather than silently rounded.
     */
    public static BigDecimal of(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessRuleException("invalid-amount", "Amount is required");
        }
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessRuleException("invalid-amount", "Amount must have at most " + SCALE + " decimal places");
        }
    }

    public static BigDecimal positive(BigDecimal amount) {
        BigDecimal normalised = of(amount);
        if (normalised.signum() <= 0) {
            throw new BusinessRuleException("invalid-amount", "Amount must be greater than zero");
        }
        return normalised;
    }
}
