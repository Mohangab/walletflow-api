package com.mohan.walletflow.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first ADMIN account from configuration (ADMIN_EMAIL / ADMIN_PASSWORD) so that
 * admin-only endpoints such as ledger reconciliation are usable without manual SQL.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AdminProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            return;
        }
        if (users.existsByEmailIgnoreCase(properties.email())) {
            return;
        }
        users.save(new User(properties.email().toLowerCase(), passwordEncoder.encode(properties.password()),
                "Administrator", Role.ADMIN));
        log.info("Bootstrapped admin user {}", properties.email());
    }

    @ConfigurationProperties(prefix = "app.admin")
    public record AdminProperties(String email, String password) {
    }
}
