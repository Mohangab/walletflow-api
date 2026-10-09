package com.mohan.walletflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.mohan.walletflow.support.IntegrationTestBase;

class PaymentFlowIT extends IntegrationTestBase {

    private ResultActions postMoney(String token, String path, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private Map<String, Object> transfer(String from, String to, String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceWalletId", from);
        body.put("destinationWalletId", to);
        body.put("amount", amount);
        return body;
    }

    @Test
    void depositWithdrawAndTransferUpdateBalances() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(bob, "INR");

        JsonNode dep = deposit(alice, aliceWallet, "500.00");
        assertThat(dep.get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(dep.get("walletBalanceAfter").decimalValue()).isEqualByComparingTo("500.00");

        postMoney(alice, "/api/v1/wallets/" + aliceWallet + "/withdrawals", Map.of("amount", "120.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.walletBalanceAfter").value(379.50));

        Map<String, Object> body = transfer(aliceWallet, bobWallet, "79.50");
        body.put("description", "Dinner split");
        postMoney(alice, "/api/v1/transfers", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.walletBalanceAfter").value(300.00));

        assertThat(balanceOf(alice, aliceWallet)).isEqualTo("300.00");
        assertThat(balanceOf(bob, bobWallet)).isEqualTo("79.50");
    }

    @Test
    void transferByRecipientEmail() throws Exception {
        String alice = newUserToken();
        String bobEmail = uniqueEmail();
        String bob = registerAndLogin(bobEmail).get("accessToken").asText();
        String aliceWallet = openWallet(alice, "USD");
        String bobWallet = openWallet(bob, "USD");
        deposit(alice, aliceWallet, "50");

        postMoney(alice, "/api/v1/transfers", Map.of("sourceWalletId", aliceWallet, "recipientEmail", bobEmail,
                "amount", "20.25"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinationWalletId").value(bobWallet));

        assertThat(balanceOf(bob, bobWallet)).isEqualTo("20.25");
    }

    @Test
    void insufficientFundsIsRejectedAndNothingMoves() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(bob, "INR");
        deposit(alice, aliceWallet, "10.00");

        postMoney(alice, "/api/v1/transfers", transfer(aliceWallet, bobWallet, "10.01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("insufficient-funds"));
        postMoney(alice, "/api/v1/wallets/" + aliceWallet + "/withdrawals", Map.of("amount", "11"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(balanceOf(alice, aliceWallet)).isEqualTo("10.00");
        assertThat(balanceOf(bob, bobWallet)).isEqualTo("0.00");
    }

    @Test
    void currencyMismatchAndSystemWalletTargetsAreRejected() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobUsd = openWallet(bob, "USD");
        deposit(alice, aliceWallet, "10.00");

        postMoney(alice, "/api/v1/transfers", transfer(aliceWallet, bobUsd, "1.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("currency-mismatch"));
        postMoney(alice, "/api/v1/transfers", transfer(aliceWallet, "00000000-0000-0000-0000-000000000001", "1.00"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidAmountsFailValidation() throws Exception {
        String alice = newUserToken();
        String wallet = openWallet(alice, "EUR");

        postMoney(alice, "/api/v1/wallets/" + wallet + "/deposits", Map.of("amount", "10.001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        postMoney(alice, "/api/v1/wallets/" + wallet + "/deposits", Map.of("amount", "-5"))
                .andExpect(status().isBadRequest());
        postMoney(alice, "/api/v1/transfers", Map.of("sourceWalletId", wallet, "amount", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.recipientSpecifiedOnce").exists());
    }

    @Test
    void cannotDepositIntoSomeoneElsesWallet() throws Exception {
        String bobWallet = openWallet(newUserToken(), "INR");

        postMoney(newUserToken(), "/api/v1/wallets/" + bobWallet + "/deposits", Map.of("amount", "5"))
                .andExpect(status().isNotFound());
    }
}
