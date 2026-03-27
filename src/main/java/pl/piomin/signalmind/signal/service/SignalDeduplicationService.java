package pl.piomin.signalmind.signal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.time.Duration;

/**
 * SM-27: Redis-backed signal deduplication with a 30-minute cooldown window.
 *
 * <p>Before a detector generates a signal, the engine calls
 * {@link #isDuplicate(Stock, SignalType)}.  If a signal of the same type was
 * already generated for the same stock within the last 30 minutes, the call
 * returns {@code true} and the detector is skipped.
 *
 * <p>Implemented with a Redis SET NX EX operation — sub-millisecond latency
 * and atomic check-and-set in a single round-trip.
 *
 * <h2>Graceful degradation</h2>
 * If Redis is unavailable the service logs a warning and returns {@code false}
 * (not a duplicate), allowing the engine to proceed without rate-limiting.
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class SignalDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(SignalDeduplicationService.class);

    /** Redis key prefix for deduplication entries. */
    static final String KEY_PREFIX = "dedup:signal:";

    /** Cooldown duration: 30 minutes per PRD Section 6.9. */
    static final Duration COOLDOWN = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public SignalDeduplicationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Checks whether a signal of the given type was already generated for this
     * stock within the 30-minute cooldown window.
     *
     * <p>Side-effect: if <em>not</em> a duplicate, marks the slot as taken in
     * Redis (SET NX EX 1800).  The caller must not call this method and then
     * discard the signal — doing so wastes the slot for 30 minutes.
     *
     * @param stock the target stock
     * @param type  the signal type being considered
     * @return {@code true} if a duplicate exists (skip this detector);
     *         {@code false} if the slot is free (proceed with detection)
     */
    public boolean isDuplicate(Stock stock, SignalType type) {
        String key = buildKey(stock.getId(), type);
        try {
            // setIfAbsent returns TRUE when the key was newly set (not a dup)
            // and FALSE (or null) when the key already existed (is a dup)
            Boolean set = redis.opsForValue().setIfAbsent(key, "1", COOLDOWN);
            return Boolean.FALSE.equals(set);
        } catch (Exception e) {
            log.warn("[dedup] Redis unavailable — skipping dedup check for {}/{}: {}",
                    stock.getSymbol(), type, e.getMessage());
            return false; // fail-open: allow signal generation
        }
    }

    /**
     * Clears the deduplication entry for the given stock and signal type.
     * Intended for testing and administrative use only.
     */
    public void clearEntry(Stock stock, SignalType type) {
        redis.delete(buildKey(stock.getId(), type));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String buildKey(Long stockId, SignalType type) {
        return KEY_PREFIX + stockId + ":" + type.name();
    }
}
