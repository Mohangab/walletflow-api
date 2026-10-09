package com.mohan.walletflow.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.mohan.walletflow.support.IntegrationTestBase;

class HistoryIT extends IntegrationTestBase {

    private String alice;
    private String bob;
    private String aliceWallet;
    private String bobWallet;
    private String transferId;

    @BeforeEach
    void seed() throws Exception {
        alice = newUserToken();
        bob = newUserToken();
        aliceWallet = openWallet(alice, "INR");
        bobWallet = openWallet(bob, "INR");
        deposit(alice, aliceWallet, "100.00");
        deposit(alice, aliceWallet, "50.00");
        transferId = read(mvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceWalletId", aliceWallet, "destinationWalletId", bobWallet,
                        "amount", "30.00", "description", "Movie tickets"))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        mvc.perform(post("/api/v1/wallets/" + aliceWallet + "/withdrawals").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("amount", "20.00")))).andExpect(status().isCreated());
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/transactions?size=3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(100.00));
        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/transactions?size=3&page=1")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].amount").value(100.00));
    }

    @Test
    void historyCanBeFilteredByTypeDirectionAndTime() throws Exception {
        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/transactions?type=TRANSFER")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].counterpartyWalletId").value(bobWallet))
                .andExpect(jsonPath("$.content[0].description").value("Movie tickets"));
        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/transactions?direction=CREDIT&sort=amount,asc")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(50.00));
        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/transactions?from=2999-01-01T00:00:00Z")
                .andExpect(jsonPath("$.totalElements").value(0));
        getAs(bob, "/api/v1/wallets/" + bobWallet + "/transactions")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].counterpartyWalletId").value(aliceWallet));
    }

    @Test
    void invalidQueriesAreRejected() throws Exception {
        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/transactions?sort=walletId")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-query"));
        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/transactions?type=REFUND")
                .andExpect(status().isBadRequest());
        getAs(bob, "/api/v1/wallets/" + aliceWallet + "/transactions")
                .andExpect(status().isNotFound());
    }

    @Test
    void statementReportsOpeningAndClosingBalancesFromTheLedger() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        JsonNode statement = read(getAs(alice, "/api/v1/wallets/" + aliceWallet + "/statement?from=" + today.minusDays(7)
                + "&to=" + today).andExpect(status().isOk()).andReturn());

        assertThat(statement.get("openingBalance").decimalValue()).isEqualByComparingTo("0");
        assertThat(statement.get("totalCredits").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(statement.get("totalDebits").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(statement.get("closingBalance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(statement.get("entryCount").asInt()).isEqualTo(4);
        assertThat(statement.get("entries").get(0).get("type").asText()).isEqualTo("DEPOSIT");

        JsonNode future = read(getAs(alice, "/api/v1/wallets/" + aliceWallet + "/statement?from=" + today.plusDays(1)
                + "&to=" + today.plusDays(2)).andReturn());
        assertThat(future.get("openingBalance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(future.get("closingBalance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(future.get("entryCount").asInt()).isZero();

        getAs(alice, "/api/v1/wallets/" + aliceWallet + "/statement?from=" + today + "&to=" + today.minusDays(1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transactionIsVisibleToParticipantsOnly() throws Exception {
        getAs(alice, "/api/v1/transactions/" + transferId).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30.00));
        getAs(bob, "/api/v1/transactions/" + transferId).andExpect(status().isOk());
        getAs(newUserToken(), "/api/v1/transactions/" + transferId).andExpect(status().isNotFound());
    }
}
