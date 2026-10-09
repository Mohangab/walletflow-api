package com.mohan.walletflow.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.mohan.walletflow.support.IntegrationTestBase;

class WalletIT extends IntegrationTestBase {

    @Test
    void openListAndGetWallet() throws Exception {
        String token = newUserToken();

        String id = read(mvc.perform(post("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("currency", "inr"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andReturn()).get("id").asText();

        mvc.perform(get("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/v1/wallets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void secondWalletInSameCurrencyConflicts() throws Exception {
        String token = newUserToken();
        String body = json(Map.of("currency", "USD"));
        mvc.perform(post("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("wallet-exists"));
    }

    @Test
    void unsupportedCurrencyIsUnprocessable() throws Exception {
        mvc.perform(post("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, bearer(newUserToken()))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("currency", "JPY"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("unsupported-currency"));
    }

    @Test
    void otherUsersWalletIsNotVisible() throws Exception {
        String owner = newUserToken();
        String id = read(mvc.perform(post("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("currency", "EUR"))))
                .andReturn()).get("id").asText();

        mvc.perform(get("/api/v1/wallets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(newUserToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }
}
