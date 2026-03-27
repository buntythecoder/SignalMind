package pl.piomin.signalmind.candle.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis-backed implementation of {@link CandleRollingBuffer}.
 *
 * <p>Close prices are stored in a Redis List keyed by
 * {@code candle:closes:{symbol}}. The list is kept as a <em>left-push</em>
 * structure so index 0 always holds the <strong>newest</strong> close and
 * index N−1 holds the oldest. The list is trimmed to at most
 * {@value #MAX_SIZE} entries after every push.
 *
 * <p>When reading, the required slice is reversed before being returned so
 * that callers receive prices in oldest-first order as expected by
 * {@link RsiCalculator#compute(java.util.List)}.
 *
 * <p>This bean is only registered when a {@link StringRedisTemplate} is
 * present in the application context; the assembler falls back to RSI=null
 * when no buffer bean is available.
 *
 * <p>SM-19
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisCandleRollingBuffer implements CandleRollingBuffer {

    /** Maximum number of close prices retained per symbol. */
    static final int MAX_SIZE = 60;

    private static final String KEY_PREFIX = "candle:closes:";

    private final StringRedisTemplate redis;

    public RedisCandleRollingBuffer(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void pushClose(String symbol, BigDecimal close) {
        String key = KEY_PREFIX + symbol;
        redis.opsForList().leftPush(key, close.toPlainString());
        redis.opsForList().trim(key, 0, MAX_SIZE - 1);
    }

    @Override
    public List<BigDecimal> getCloses(String symbol, int count) {
        String key = KEY_PREFIX + symbol;
        List<String> raw = redis.opsForList().range(key, 0, count - 1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        // Redis stores newest-first; reverse to oldest-first for RSI calculation
        List<String> reversed = new ArrayList<>(raw);
        Collections.reverse(reversed);
        return reversed.stream()
                .map(BigDecimal::new)
                .collect(Collectors.toList());
    }

    @Override
    public String bufferName() {
        return "redis-candle-rolling-buffer";
    }
}
