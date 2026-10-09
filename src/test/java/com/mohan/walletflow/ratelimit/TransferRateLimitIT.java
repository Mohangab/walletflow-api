package com.mohan.walletflow.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.mohan.walletflow.support.IntegrationTestBase;

@TestPropertySource(properties = {
        "app.rate-limit.capacity=3",
        "app.rate-limit.refill-tokens=3",
        "app.rate-limit.refill-period=1h"
})
class TransferRateLimitIT extends IntegrationTestBase {

    private ResultActions transfer(String token, String from, String to) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceWalletId", from, "destinationWalletId", to, "amount", "1.00"))));
    }

    @Test
    void fourthTransferWithinTheWindowIsRejectedWith429() throws Exception {
        String alice = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(newUserToken(), "INR");
        deposit(alice, aliceWallet, "100");

        transfer(alice, aliceWallet, bobWallet).andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "2"));
        transfer(alice, aliceWallet, bobWallet).andExpect(status().isCreated());
        transfer(alice, aliceWallet, bobWallet).andExpect(status().isCreated());

        transfer(alice, aliceWallet, bobWallet)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("rate-limited"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void limitsArePerUserAndDoNotApplyToDeposits() throws Exception {
        String carol = newUserToken();
        String carolWallet = openWallet(carol, "USD");
        String daveWallet = openWallet(newUserToken(), "USD");
        for (int i = 0; i < 5; i++) {
            deposit(carol, carolWallet, "1");
        }
        transfer(carol, carolWallet, daveWallet).andExpect(status().isCreated());
    }
}
