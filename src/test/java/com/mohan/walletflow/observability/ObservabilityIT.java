package com.mohan.walletflow.observability;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.mohan.walletflow.support.IntegrationTestBase;

/** Spring Boot tests disable metrics export by default; re-enable it to scrape /actuator/prometheus. */
@AutoConfigureObservability
class ObservabilityIT extends IntegrationTestBase {

    @Test
    void transferMetricsAreExposedToPrometheus() throws Exception {
        String alice = newUserToken();
        String aliceWallet = openWallet(alice, "INR");
        String bobWallet = openWallet(newUserToken(), "INR");
        deposit(alice, aliceWallet, "5.00");
        for (String amount : new String[] { "1.00", "999.00" }) {
            mvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                    .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("sourceWalletId", aliceWallet, "destinationWalletId", bobWallet,
                            "amount", amount))));
        }

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("walletflow_transfers_total{")))
                .andExpect(content().string(containsString("walletflow_transfer_failures_total{")))
                .andExpect(content().string(containsString("reason=\"insufficient-funds\"")))
                .andExpect(content().string(containsString("walletflow_transfer_duration_seconds_bucket")))
                .andExpect(content().string(containsString("walletflow_reconciliation_healthy")));
    }

    @Test
    void healthIsPublicButDetailedMetricsNeedAdmin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(newUserToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentsBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.parameters[?(@.name == 'Idempotency-Key')]").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
