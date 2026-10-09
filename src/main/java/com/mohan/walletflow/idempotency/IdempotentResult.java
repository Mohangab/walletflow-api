package com.mohan.walletflow.idempotency;

/**
 * @param body     the response body (fresh or replayed)
 * @param replayed true when the body was served from a stored response instead of executing again
 */
public record IdempotentResult<T>(T body, boolean replayed) {
}
