package com.mohan.walletflow.admin;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mohan.walletflow.admin.dto.ReconciliationReport;
import com.mohan.walletflow.wallet.dto.WalletResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Ledger reconciliation and wallet controls (ADMIN role)")
public class AdminController {

    private final ReconciliationService reconciliation;
    private final AdminWalletService adminWallets;

    public AdminController(ReconciliationService reconciliation, AdminWalletService adminWallets) {
        this.reconciliation = reconciliation;
        this.adminWallets = adminWallets;
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Verify stored balances against the ledger, journal balance and trial balance")
    public ReconciliationReport reconcile() {
        return reconciliation.reconcile();
    }

    @PostMapping("/wallets/{walletId}/freeze")
    @Operation(summary = "Freeze a wallet: it can no longer send or receive money")
    public WalletResponse freeze(@PathVariable UUID walletId) {
        return adminWallets.freeze(walletId);
    }

    @PostMapping("/wallets/{walletId}/unfreeze")
    @Operation(summary = "Unfreeze a wallet")
    public WalletResponse unfreeze(@PathVariable UUID walletId) {
        return adminWallets.unfreeze(walletId);
    }
}
