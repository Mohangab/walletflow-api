package com.mohan.walletflow.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.mohan.walletflow.payment.MoneyMovementService;
import com.mohan.walletflow.payment.dto.TransactionResponse;
import com.mohan.walletflow.payment.dto.TransferRequest;
import com.mohan.walletflow.security.AuthenticatedUser;
import com.mohan.walletflow.security.JwtService;
import com.mohan.walletflow.support.IntegrationTestBase;

class IdempotencyIT extends IntegrationTestBase {

    @Autowired
    private MoneyMovementService payments;
    @Autowired
    private JwtService jwtService;

    private ResultActions transfer(String token, String key, String from, String to, String amount) throws Exception {
        var request = post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceWalletId", from, "destinationWalletId", to, "amount", amount)));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    @Test
    void replayingTheSameRequestReturnsTheOriginalResultAndMovesMoneyOnce() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(bob, "INR");
        deposit(alice, aliceWallet, "100.00");
        String key = UUID.randomUUID().toString();

        JsonNode first = read(transfer(alice, key, aliceWallet, bobWallet, "25.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn());
        JsonNode second = read(transfer(alice, key, aliceWallet, bobWallet, "25.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn());

        assertThat(second).isEqualTo(first);
        assertThat(balanceOf(alice, aliceWallet)).isEqualTo("75.00");
        assertThat(balanceOf(bob, bobWallet)).isEqualTo("25.00");
    }

    @Test
    void reusingAKeyWithADifferentPayloadIsRejected() throws Exception {
        String alice = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(newUserToken(), "INR");
        deposit(alice, aliceWallet, "100.00");
        String key = UUID.randomUUID().toString();

        transfer(alice, key, aliceWallet, bobWallet, "10.00").andExpect(status().isCreated());
        transfer(alice, key, aliceWallet, bobWallet, "99.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency-key-reused"));

        assertThat(balanceOf(alice, aliceWallet)).isEqualTo("90.00");
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        String alice = newUserToken();
        String carol = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String carolWallet = openWallet(carol, "INR");
        String bobWallet = openWallet(newUserToken(), "INR");
        deposit(alice, aliceWallet, "10.00");
        deposit(carol, carolWallet, "10.00");
        String shared = "order-42";

        transfer(alice, shared, aliceWallet, bobWallet, "1.00").andExpect(header().string("Idempotent-Replayed", "false"));
        transfer(carol, shared, carolWallet, bobWallet, "1.00").andExpect(header().string("Idempotent-Replayed", "false"));
    }

    @Test
    void failedRequestsAreNotStoredSoTheKeyCanBeRetried() throws Exception {
        String alice = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(newUserToken(), "INR");
        String key = UUID.randomUUID().toString();

        transfer(alice, key, aliceWallet, bobWallet, "5.00").andExpect(status().isUnprocessableEntity());
        deposit(alice, aliceWallet, "5.00");
        transfer(alice, key, aliceWallet, bobWallet, "5.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
    }

    @Test
    void missingOrMalformedKeyIsABadRequest() throws Exception {
        String alice = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(newUserToken(), "INR");

        transfer(alice, null, aliceWallet, bobWallet, "1.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing-header"));
        transfer(alice, "bad key!", aliceWallet, bobWallet, "1.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-idempotency-key"));
    }

    @Test
    void concurrentDuplicatesExecuteExactlyOnce() throws Exception {
        String aliceEmail = uniqueEmail();
        JsonNode tokens = registerAndLogin(aliceEmail);
        String alice = tokens.get("accessToken").asText();
        String aliceWallet = openWallet(alice, "EUR");
        String bobWallet = openWallet(newUserToken(), "EUR");
        deposit(alice, aliceWallet, "100.00");
        AuthenticatedUser principal = jwtService.verifyAccessToken(alice).orElseThrow();
        TransferRequest request = new TransferRequest(UUID.fromString(aliceWallet), UUID.fromString(bobWallet), null,
                new BigDecimal("30.00"), "once");
        String key = UUID.randomUUID().toString();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<TransactionResponse>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return payments.transfer(principal, request, key).body();
            }));
        }
        go.countDown();
        Set<UUID> transactionIds = ConcurrentHashMap.newKeySet();
        for (Future<TransactionResponse> f : results) {
            transactionIds.add(f.get(1, TimeUnit.MINUTES).id());
        }
        pool.shutdown();

        assertThat(transactionIds).hasSize(1);
        assertThat(balanceOf(alice, aliceWallet)).isEqualTo("70.00");
    }
}
