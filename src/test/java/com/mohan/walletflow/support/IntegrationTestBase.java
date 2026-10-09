package com.mohan.walletflow.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Str0ngPassw0rd!";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@walletflow.test";
    }

    protected JsonNode registerAndLogin(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", PASSWORD, "fullName", "Test User"))))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    protected JsonNode login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    /** Registers a fresh user and returns its access token. */
    protected String newUserToken() throws Exception {
        return registerAndLogin(uniqueEmail()).get("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String openWallet(String token, String currency) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/wallets").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("currency", currency))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asText();
    }

    protected JsonNode deposit(String token, String walletId, String amount) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/wallets/" + walletId + "/deposits")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", amount))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result);
    }

    protected String balanceOf(String token, String walletId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/wallets/" + walletId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("balance").decimalValue().setScale(2).toPlainString();
    }
}
