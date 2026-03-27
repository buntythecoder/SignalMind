package pl.piomin.signalmind.ingestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pl.piomin.signalmind.ingestion.domain.MarketTick;

import java.time.Duration;

/**
 * Publishes the latest market tick for each symbol to Redis as a JSON string.
 *
 * <p>The key format is {@code tick:<symbol>} and each entry expires after 2 minutes.
 * Only activated when a {@link StringRedisTemplate} bean is present in the context
 * (i.e., Redis is configured and available — excluded in the {@code test} profile).
 */
@Component("redisTickBuffer")
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisTickBuffer implements TickBuffer {

    private static final Logger log = LoggerFactory.getLogger(RedisTickBuffer.class);
    private static final Duration TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;

    public RedisTickBuffer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String bufferName() {
        return "redis";
    }

    @Override
    public void publish(MarketTick tick) {
        // Inline JSON serialization — no Jackson injection required.
        // Fields: symbol, ltp, volume, ts (ISO-8601 receivedAt).
        String json = "{\"symbol\":\"%s\",\"ltp\":%s,\"volume\":%d,\"ts\":\"%s\"}"
                .formatted(tick.symbol(), tick.ltp(), tick.volume(), tick.receivedAt());

        redisTemplate.opsForValue().set("tick:" + tick.symbol(), json, TTL);

        log.trace("[redis] published tick for {}: ltp={}", tick.symbol(), tick.ltp());
    }
}
