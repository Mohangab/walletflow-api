package com.mohan.walletflow.wallet;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mohan.walletflow.security.AuthenticatedUser;
import com.mohan.walletflow.wallet.dto.CreateWalletRequest;
import com.mohan.walletflow.wallet.dto.WalletResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "One wallet per user per currency")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @Operation(summary = "Open a wallet in a supported currency (INR, USD, EUR, GBP)")
    public ResponseEntity<WalletResponse> open(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateWalletRequest request) {
        WalletResponse wallet = walletService.open(user.id(), request.currency());
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + wallet.id())).body(wallet);
    }

    @GetMapping
    @Operation(summary = "List my wallets")
    public List<WalletResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return walletService.listFor(user.id());
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get a wallet with its current balance")
    public WalletResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID walletId) {
        return walletService.get(user, walletId);
    }
}
