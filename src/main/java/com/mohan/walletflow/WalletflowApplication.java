package com.mohan.walletflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WalletflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletflowApplication.class, args);
    }
}
