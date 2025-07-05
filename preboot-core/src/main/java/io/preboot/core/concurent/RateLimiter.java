package io.preboot.core.concurent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;

/** Limits the rate of API calls for specific keys. Uses Token Bucket algorithm to control request flow. */
public class RateLimiter {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RateLimiter.class);

    private final int defaultRateLimit;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int defaultRateLimit) {
        this.defaultRateLimit = defaultRateLimit;
        log.debug("Rate limiter initialized with default rate of {} requests per second", defaultRateLimit);
    }

    /**
     * Attempts to acquire permission to proceed with an API call for the given client ID. If rate limit is exceeded,
     * this method will block until a token becomes available.
     *
     * @param clientId The client ID for which to acquire permission
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void acquire(String clientId) throws InterruptedException {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, id -> new TokenBucket(defaultRateLimit));
        bucket.acquire();
    }

    /**
     * Attempts to acquire permission without blocking.
     *
     * @param clientId The client ID for which to acquire permission
     * @return true if permission was acquired, false if the rate limit was exceeded
     */
    public boolean tryAcquire(String clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, id -> new TokenBucket(defaultRateLimit));
        return bucket.tryAcquire();
    }

    /**
     * Executes the given operation with rate limiting for the specified client ID.
     *
     * @param clientId The client ID to apply rate limiting
     * @param operation The operation to execute
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws InterruptedException If the thread is interrupted while waiting for rate limit
     */
    public <T> T executeWithRateLimit(String clientId, Supplier<T> operation) throws InterruptedException {
        // Apply rate limiting
        acquire(clientId);

        // Execute the operation
        return operation.get();
    }

    /**
     * Executes the given operation with rate limiting for the specified client ID.
     *
     * @param clientId The client ID to apply rate limiting
     * @param operation The operation to execute
     * @throws InterruptedException If the thread is interrupted while waiting for rate limit
     */
    public void executeWithRateLimit(String clientId, Runnable operation) throws InterruptedException {
        // Apply rate limiting
        acquire(clientId);

        // Execute the operation
        operation.run();
    }

    /**
     * Sets a custom rate limit for a specific client ID.
     *
     * @param clientId The client ID for which to set the rate limit
     * @param rateLimit The maximum number of requests per second
     */
    public void setRateLimit(String clientId, int rateLimit) {
        buckets.put(clientId, new TokenBucket(rateLimit));
        log.debug("Set custom rate limit of {} requests per second for client ID '{}'", rateLimit, clientId);
    }

    /** Token bucket implementation for rate limiting. */
    private static class TokenBucket {
        private final int tokensPerSecond;
        private double tokens;
        private long lastRefillTimestamp;
        private final Lock lock = new ReentrantLock();

        public TokenBucket(int tokensPerSecond) {
            this.tokensPerSecond = tokensPerSecond;
            this.tokens = tokensPerSecond;
            this.lastRefillTimestamp = Instant.now().toEpochMilli();
        }

        public void acquire() throws InterruptedException {
            while (!tryAcquire()) {
                // Wait a bit before trying again
                log.debug(
                        "RateLimiter kicks in, waiting for tokens to be acquired {}ms",
                        Math.max(50, 1000 / tokensPerSecond));
                TimeUnit.MILLISECONDS.sleep(Math.max(50, 1000 / tokensPerSecond));
            }
        }

        public boolean tryAcquire() {
            lock.lock();
            try {
                refill();

                if (tokens >= 1.0) {
                    tokens -= 1.0;
                    return true;
                }

                return false;
            } finally {
                lock.unlock();
            }
        }

        private void refill() {
            long now = Instant.now().toEpochMilli();
            double tokensToAdd = (now - lastRefillTimestamp) * tokensPerSecond / 1000.0;

            if (tokensToAdd > 0) {
                tokens = Math.min(tokensPerSecond, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
