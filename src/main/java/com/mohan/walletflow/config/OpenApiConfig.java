package com.mohan.walletflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger UI with a global Bearer (JWT) scheme: click "Authorize" and paste the access token. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI walletflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WalletFlow API")
                        .version("v1")
                        .description("Digital wallet & payments API: double-entry ledger, idempotent money movement, "
                                + "per-user rate limiting. Log in via /api/v1/auth/login, then Authorize with the "
                                + "accessToken.")
                        .contact(new Contact().name("Kola Mohan Venkatesh").url("https://github.com/Mohangab"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from /api/v1/auth/login or /api/v1/auth/refresh")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
