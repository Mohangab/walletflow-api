package com.mohan.walletflow.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mohan.walletflow.common.error.ApiException;
import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.tx.TransactionRunner;
import com.mohan.walletflow.payment.dto.DepositRequest;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository records;
    @Mock
    private TransactionRunner txRunner;

    private IdempotencyService service;
    private final UUID user = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-06-01T12:00:00Z");

    record Body(String id, BigDecimal amount) {
    }

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(records, txRunner, new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24));
    }

    @Test
    @SuppressWarnings("unchecked")
    void firstRequestExecutesAndStoresResponse() {
        when(records.findByUserIdAndIdempotencyKey(user, "k1")).thenReturn(Optional.empty());
        when(txRunner.inTransactionWithRetry(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());

        IdempotentResult<Body> result = service.execute(user, "k1", "op", new DepositRequest(BigDecimal.TEN, null),
                Body.class, () -> new Body("tx-1", BigDecimal.TEN));

        assertThat(result.replayed()).isFalse();
        assertThat(result.body().id()).isEqualTo("tx-1");
        verify(records).saveAndFlush(any(IdempotencyRecord.class));
    }

    @Test
    void sameKeyAndPayloadReplaysWithoutExecuting() {
        DepositRequest request = new DepositRequest(BigDecimal.TEN, "x");
        String hash = service.fingerprint("op", request);
        when(records.findByUserIdAndIdempotencyKey(user, "k1")).thenReturn(Optional.of(new IdempotencyRecord(user, "k1",
                hash, 201, "{\"id\":\"tx-1\",\"amount\":10}", now, now.plusSeconds(60))));
        AtomicInteger executions = new AtomicInteger();

        IdempotentResult<Body> result = service.execute(user, "k1", "op", request, Body.class, () -> {
            executions.incrementAndGet();
            return new Body("tx-2", BigDecimal.ONE);
        });

        assertThat(result.replayed()).isTrue();
        assertThat(result.body().id()).isEqualTo("tx-1");
        assertThat(executions).hasValue(0);
        verify(txRunner, never()).inTransactionWithRetry(any());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        String hash = service.fingerprint("op", new DepositRequest(BigDecimal.TEN, null));
        when(records.findByUserIdAndIdempotencyKey(user, "k1")).thenReturn(Optional.of(
                new IdempotencyRecord(user, "k1", hash, 201, "{}", now, now.plusSeconds(60))));

        assertThatThrownBy(() -> service.execute(user, "k1", "op", new DepositRequest(BigDecimal.ONE, null),
                Body.class, () -> null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("idempotency-key-reused");
    }

    @Test
    void fingerprintDependsOnOperationAndPayload() {
        DepositRequest request = new DepositRequest(BigDecimal.TEN, null);

        assertThat(service.fingerprint("op", request)).isEqualTo(service.fingerprint("op", request)).hasSize(64);
        assertThat(service.fingerprint("op", request)).isNotEqualTo(service.fingerprint("other", request));
        assertThat(service.fingerprint("op", request))
                .isNotEqualTo(service.fingerprint("op", new DepositRequest(BigDecimal.ONE, null)));
    }

    @Test
    void malformedKeysAreRejected() {
        assertThatThrownBy(() -> IdempotencyService.validateKey("has spaces")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> IdempotencyService.validateKey("x".repeat(101))).isInstanceOf(ApiException.class);
        IdempotencyService.validateKey("3f1c6a1e-8a8b-4c43-9d6c-0f0b7b5d2a11");
    }
}
