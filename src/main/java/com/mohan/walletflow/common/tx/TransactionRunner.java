package com.mohan.walletflow.common.tx;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work in a fresh transaction and retries it on transient concurrency failures
 * (optimistic version conflicts, lock timeouts, deadlock victims, serialization failures).
 * Each retry starts a brand-new transaction, so no stale entity state leaks between attempts.
 */
@Component
public class TransactionRunner {

    private static final Logger log = LoggerFactory.getLogger(TransactionRunner.class);

    private final TransactionTemplate template;
    private final int maxAttempts;
    private final long backoffMillis;

    public TransactionRunner(PlatformTransactionManager transactionManager,
            @Value("${app.tx.max-attempts:4}") int maxAttempts,
            @Value("${app.tx.backoff-millis:25}") long backoffMillis) {
        this.template = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    public <T> T inTransactionWithRetry(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (ConcurrencyFailureException ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                log.debug("Concurrency failure on attempt {}/{}: {}", attempt, maxAttempts, ex.getMessage());
                sleep(backoffMillis * attempt);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis + (long) (Math.random() * millis));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off", ie);
        }
    }
}
