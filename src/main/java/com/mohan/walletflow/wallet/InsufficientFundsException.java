package com.mohan.walletflow.wallet;

import com.mohan.walletflow.common.error.BusinessRuleException;

public class InsufficientFundsException extends BusinessRuleException {

    public InsufficientFundsException() {
        super("insufficient-funds", "Wallet balance is insufficient for this operation");
    }
}
