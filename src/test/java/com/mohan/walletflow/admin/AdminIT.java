package com.mohan.walletflow.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.mohan.walletflow.support.IntegrationTestBase;

class AdminIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = login("admin@walletflow.test", "Admin#Pass123").get("accessToken").asText();
    }

    private ResultActions transfer(String token, String from, String to) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceWalletId", from, "destinationWalletId", to, "amount", "1.00"))));
    }

    @Test
    void regularUsersCannotUseAdminEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/reconciliation").header(HttpHeaders.AUTHORIZATION, bearer(newUserToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void reconciliationIsHealthyAfterRealActivity() throws Exception {
        String alice = newUserToken();
        String aliceWallet = openWallet(alice, "USD");
        String bobWallet = openWallet(newUserToken(), "USD");
        deposit(alice, aliceWallet, "42.00");
        transfer(alice, aliceWallet, bobWallet).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/admin/reconciliation").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.mismatches.length()").value(0))
                .andExpect(jsonPath("$.unbalancedTransactions.length()").value(0))
                .andExpect(jsonPath("$.trialBalance[?(@.currency == 'USD')].balanced").value(true));
    }

    @Test
    void reconciliationDetectsABalanceThatDriftedFromTheLedger() throws Exception {
        String alice = newUserToken();
        String wallet = openWallet(alice, "EUR");
        deposit(alice, wallet, "10.00");
        // Simulate a bug / manual edit that bypassed the ledger.
        jdbc.update("update wallets set balance = balance + 5 where id = ?", UUID.fromString(wallet));
        try {
            mvc.perform(get("/api/v1/admin/reconciliation").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(jsonPath("$.healthy").value(false))
                    .andExpect(jsonPath("$.mismatches[0].walletId").value(wallet))
                    .andExpect(jsonPath("$.mismatches[0].storedBalance").value(15.00))
                    .andExpect(jsonPath("$.mismatches[0].ledgerBalance").value(10.00))
                    .andExpect(jsonPath("$.mismatches[0].difference").value(5.00));
        } finally {
            jdbc.update("update wallets set balance = balance - 5 where id = ?", UUID.fromString(wallet));
        }
    }

    @Test
    void frozenWalletsCannotSendOrReceive() throws Exception {
        String alice = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(newUserToken(), "INR");
        deposit(alice, aliceWallet, "10.00");

        mvc.perform(post("/api/v1/admin/wallets/" + bobWallet + "/freeze").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        transfer(alice, aliceWallet, bobWallet)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("wallet-frozen"));

        mvc.perform(post("/api/v1/admin/wallets/" + bobWallet + "/unfreeze").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        transfer(alice, aliceWallet, bobWallet).andExpect(status().isCreated());
    }

    @Test
    void settlementWalletsCannotBeFrozen() throws Exception {
        mvc.perform(post("/api/v1/admin/wallets/00000000-0000-0000-0000-000000000001/freeze")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableEntity());
    }
}
