package com.mohan.walletflow.payment;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.error.NotFoundException;
import com.mohan.walletflow.common.tx.TransactionRunner;
import com.mohan.walletflow.ledger.LedgerService;
import com.mohan.walletflow.ledger.LedgerService.Posting;
import com.mohan.walletflow.ledger.TransactionType;
import com.mohan.walletflow.payment.dto.DepositRequest;
import com.mohan.walletflow.payment.dto.TransactionResponse;
import com.mohan.walletflow.payment.dto.TransferRequest;
import com.mohan.walletflow.payment.dto.WithdrawalRequest;
import com.mohan.walletflow.security.AuthenticatedUser;
import com.mohan.walletflow.user.UserRepository;
import com.mohan.walletflow.wallet.Wallet;
import com.mohan.walletflow.wallet.WalletLocker;
import com.mohan.walletflow.wallet.WalletRef;
import com.mohan.walletflow.wallet.WalletRepository;

/**
 * Deposits, withdrawals and P2P transfers.
 * <p>
 * Each operation runs in its own transaction (retried on transient lock/version conflicts), locks all
 * wallets involved in a consistent order, validates business rules on the locked rows and posts a
 * balanced journal through {@link LedgerService}.
 */
@Service
public class MoneyMovementService {

    private final TransactionRunner txRunner;
    private final WalletLocker locker;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final LedgerService ledger;

    public MoneyMovementService(TransactionRunner txRunner, WalletLocker locker, WalletRepository wallets,
            UserRepository users, LedgerService ledger) {
        this.txRunner = txRunner;
        this.locker = locker;
        this.wallets = wallets;
        this.users = users;
        this.ledger = ledger;
    }

    /** Funds arrive from outside (bank / card rails): debit the currency's settlement account, credit the wallet. */
    public TransactionResponse deposit(AuthenticatedUser user, UUID walletId, DepositRequest request) {
        return txRunner.inTransactionWithRetry(() -> doDeposit(user, walletId, request));
    }

    public TransactionResponse withdraw(AuthenticatedUser user, UUID walletId, WithdrawalRequest request) {
        return txRunner.inTransactionWithRetry(() -> doWithdraw(user, walletId, request));
    }

    public TransactionResponse transfer(AuthenticatedUser user, TransferRequest request) {
        return txRunner.inTransactionWithRetry(() -> doTransfer(user, request));
    }

    // --- the following run inside the caller's transaction (used directly by the idempotency layer) ---

    TransactionResponse doDeposit(AuthenticatedUser user, UUID walletId, DepositRequest request) {
        WalletRef target = ownedWallet(user, walletId);
        UUID systemId = systemWalletId(target.currency());
        Map<UUID, Wallet> locked = locker.lockInOrder(systemId, target.id());
        Wallet wallet = locked.get(target.id());

        Posting posting = ledger.move(TransactionType.DEPOSIT, locked.get(systemId), wallet, request.amount(),
                request.description(), user.id());
        return TransactionResponse.from(posting.transaction(), wallet.getId(), posting.entryFor(wallet).getBalanceAfter());
    }

    TransactionResponse doWithdraw(AuthenticatedUser user, UUID walletId, WithdrawalRequest request) {
        WalletRef source = ownedWallet(user, walletId);
        UUID systemId = systemWalletId(source.currency());
        Map<UUID, Wallet> locked = locker.lockInOrder(systemId, source.id());
        Wallet wallet = locked.get(source.id());

        Posting posting = ledger.move(TransactionType.WITHDRAWAL, wallet, locked.get(systemId), request.amount(),
                request.description(), user.id());
        return TransactionResponse.from(posting.transaction(), wallet.getId(), posting.entryFor(wallet).getBalanceAfter());
    }

    TransactionResponse doTransfer(AuthenticatedUser user, TransferRequest request) {
        WalletRef source = ownedWallet(user, request.sourceWalletId());
        UUID destinationId = resolveDestination(request, source.currency());
        if (destinationId.equals(source.id())) {
            throw new BusinessRuleException("same-wallet", "Source and destination wallets must differ");
        }

        Map<UUID, Wallet> locked = locker.lockInOrder(source.id(), destinationId);
        Wallet from = locked.get(source.id());
        Wallet to = locked.get(destinationId);
        if (to.getOwnerId() == null) {
            throw new NotFoundException("Wallet", destinationId);
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("currency-mismatch",
                    "Cannot transfer " + from.getCurrency() + " to a " + to.getCurrency() + " wallet");
        }

        Posting posting = ledger.move(TransactionType.TRANSFER, from, to, request.amount(), request.description(),
                user.id());
        return TransactionResponse.from(posting.transaction(), from.getId(), posting.entryFor(from).getBalanceAfter());
    }

    private UUID resolveDestination(TransferRequest request, String currency) {
        if (request.destinationWalletId() != null) {
            return request.destinationWalletId();
        }
        UUID recipientId = users.findByEmailIgnoreCase(request.recipientEmail().trim())
                .orElseThrow(() -> new NotFoundException("Recipient", request.recipientEmail()))
                .getId();
        return wallets.findIdByOwnerIdAndCurrency(recipientId, currency)
                .orElseThrow(() -> new BusinessRuleException("recipient-wallet-missing",
                        "Recipient has no " + currency + " wallet"));
    }

    /** Ownership check without loading the entity (see {@link WalletRef}); non-owners get 404. */
    private WalletRef ownedWallet(AuthenticatedUser user, UUID walletId) {
        WalletRef wallet = wallets.findRefById(walletId).orElseThrow(() -> new NotFoundException("Wallet", walletId));
        if (!wallet.isOwnedBy(user.id())) {
            throw new NotFoundException("Wallet", walletId);
        }
        return wallet;
    }

    private UUID systemWalletId(String currency) {
        return wallets.findSystemWalletId(currency)
                .orElseThrow(() -> new IllegalStateException("No settlement wallet for " + currency));
    }
}
