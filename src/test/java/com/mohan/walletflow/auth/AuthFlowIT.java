package com.mohan.walletflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.mohan.walletflow.support.IntegrationTestBase;

class AuthFlowIT extends IntegrationTestBase {

    @Test
    void registerLoginAndCallProtectedEndpoint() throws Exception {
        String email = uniqueEmail();
        JsonNode tokens = registerAndLogin(email);

        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens.get("accessToken").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void duplicateEmailIsRejectedWithConflict() throws Exception {
        String email = uniqueEmail();
        registerAndLogin(email);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", PASSWORD, "fullName", "Again"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("email-taken"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "not-an-email", "password", "short", "fullName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String email = uniqueEmail();
        registerAndLogin(email);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid-credentials"));
    }

    @Test
    void missingOrInvalidTokenReturnsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokensAndReuseRevokesAllSessions() throws Exception {
        JsonNode first = registerAndLogin(uniqueEmail());
        String original = first.get("refreshToken").asText();

        JsonNode second = read(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", original))))
                .andExpect(status().isOk())
                .andReturn());
        String rotated = second.get("refreshToken").asText();
        assertThat(rotated).isNotEqualTo(original);

        // Replaying the old token = theft signal: rejected and the rotated token is revoked too.
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", original))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh-token-reused"));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", rotated))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode tokens = registerAndLogin(uniqueEmail());
        String refresh = tokens.get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrappedAdminCanLogIn() throws Exception {
        JsonNode tokens = login("admin@walletflow.test", "Admin#Pass123");
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens.get("accessToken").asText())))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
