package pl.piomin.signalmind.signal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SignalDeduplicationService} (SM-27).
 */
class SignalDeduplicationServiceTest {

    private StringRedisTemplate mockRedis;
    private ValueOperations<String, String> mockOps;
    private SignalDeduplicationService service;
    private Stock stock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockRedis = mock(StringRedisTemplate.class);
        mockOps   = mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(mockOps);
        service   = new SignalDeduplicationService(mockRedis);
        stock     = new Stock("TCS", "Tata Consultancy Services", IndexType.NIFTY50);
        // Give the stock a pseudo-ID via reflection is not easy; use a new stock with ID null
        // We'll check key format using the static helper method directly
    }

    @Test
    @DisplayName("buildKey produces expected format")
    void buildKey_format() {
        String key = SignalDeduplicationService.buildKey(42L, SignalType.ORB);
        assertThat(key).isEqualTo("dedup:signal:42:ORB");
    }

    @Test
    @DisplayName("isDuplicate returns false (not a dup) when Redis setIfAbsent returns true (key was newly set)")
    void isDuplicate_false_whenKeyNewlySet() {
        when(mockOps.setIfAbsent(any(), eq("1"), eq(SignalDeduplicationService.COOLDOWN)))
                .thenReturn(true); // key did not exist → not a duplicate

        assertThat(service.isDuplicate(stock, SignalType.ORB)).isFalse();
    }

    @Test
    @DisplayName("isDuplicate returns true when Redis setIfAbsent returns false (key already existed)")
    void isDuplicate_true_whenKeyAlreadyExists() {
        when(mockOps.setIfAbsent(any(), eq("1"), eq(SignalDeduplicationService.COOLDOWN)))
                .thenReturn(false); // key existed → is a duplicate

        assertThat(service.isDuplicate(stock, SignalType.ORB)).isTrue();
    }

    @Test
    @DisplayName("isDuplicate returns false (fail-open) when Redis throws an exception")
    void isDuplicate_failOpen_whenRedisThrows() {
        when(mockOps.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThat(service.isDuplicate(stock, SignalType.ORB)).isFalse();
    }

    @Test
    @DisplayName("isDuplicate returns false (fail-open) when Redis returns null")
    void isDuplicate_failOpen_whenRedisReturnsNull() {
        when(mockOps.setIfAbsent(any(), eq("1"), eq(SignalDeduplicationService.COOLDOWN)))
                .thenReturn(null); // treat null as not-set → fail-open

        assertThat(service.isDuplicate(stock, SignalType.ORB)).isFalse();
    }

    @Test
    @DisplayName("cooldown is exactly 30 minutes")
    void cooldown_is30Minutes() {
        assertThat(SignalDeduplicationService.COOLDOWN).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("clearEntry calls Redis delete with the correct key")
    void clearEntry_deletesCorrectKey() {
        // stock.getId() is null here since the entity hasn't been persisted
        String expectedKey = SignalDeduplicationService.buildKey(stock.getId(), SignalType.GAP_FILL_LONG);
        service.clearEntry(stock, SignalType.GAP_FILL_LONG);
        verify(mockRedis).delete(expectedKey);
    }
}
