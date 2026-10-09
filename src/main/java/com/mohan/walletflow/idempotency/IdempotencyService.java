package com.mohan.walletflow.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mohan.walletflow.common.error.ApiException;
import com.mohan.walletflow.common.error.BusinessRuleException;
import com.mohan.walletflow.common.tx.TransactionRunner;

/**
 * Exactly-once semantics for money-moving POSTs driven by the {@code Idempotency-Key} header.
 * <ul>
 *   <li>First request: the operation and the stored response are committed in <b>one</b> transaction.</li>
 *   <li>Retry with the same key and payload: the stored response is replayed, nothing executes again.</li>
 *   <li>Same key, different payload (or endpoint): rejected with 422.</li>
 *   <li>Two identical requests racing: the unique (user_id, key) constraint lets exactly one commit; the
 *       loser's transaction (including its ledger writes) rolls back and it replays the winner's response.</li>
 * </ul>
 * Failed requests (e.g. insufficient funds) are not stored, so a client may fix the cause and retry with the same key.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9_\\-:.]{1,100}$");

    private final IdempotencyRecordRepository records;
    private final TransactionRunner txRunner;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyService(IdempotencyRecordRepository records, TransactionRunner txRunner,
            ObjectMapper objectMapper, Clock clock, @Value("${app.idempotency.ttl:24h}") Duration ttl) {
        this.records = records;
        this.txRunner = txRunner;
        this.objectMapper = objectMapper;
        this.canonicalMapper = JsonMapper.builder()
                .findAndAddModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        this.clock = clock;
        this.ttl = ttl;
    }

    public <T> IdempotentResult<T> execute(UUID userId, String key, String operation, Object request,
            Class<T> responseType, Supplier<T> action) {
        validateKey(key);
        String requestHash = fingerprint(operation, request);

        Optional<IdempotentResult<T>> replay = replay(userId, key, requestHash, responseType);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return txRunner.inTransactionWithRetry(() -> {
                T body = action.get();
                Instant now = clock.instant();
                records.saveAndFlush(new IdempotencyRecord(userId, key, requestHash, HttpStatus.CREATED.value(),
                        write(body), now, now.plus(ttl)));
                return new IdempotentResult<>(body, false);
            });
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key committed first; our whole transaction rolled back.
            log.info("Idempotency key {} raced with a concurrent request; replaying stored response", key);
            return replay(userId, key, requestHash, responseType).orElseThrow(() -> race);
        }
    }

    private <T> Optional<IdempotentResult<T>> replay(UUID userId, String key, String requestHash, Class<T> type) {
        return records.findByUserIdAndIdempotencyKey(userId, key).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessRuleException("idempotency-key-reused",
                        "Idempotency-Key '" + key + "' was already used with a different request payload");
            }
            return new IdempotentResult<>(read(record.getResponseBody(), type), true);
        });
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval:PT1H}", initialDelayString = "PT1M")
    @Transactional
    public void purgeExpired() {
        int removed = records.deleteExpired(clock.instant());
        if (removed > 0) {
            log.info("Purged {} expired idempotency keys", removed);
        }
    }

    static void validateKey(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-idempotency-key",
                    "Idempotency-Key must be 1-100 characters of [A-Za-z0-9_-:.]");
        }
    }

    String fingerprint(String operation, Object request) {
        try {
            byte[] canonical = (operation + "\n" + canonicalMapper.writeValueAsString(request))
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint request", e);
        }
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot store idempotent response", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read stored idempotent response", e);
        }
    }
}
