package pl.piomin.signalmind.integration.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TelegramSubscriberService}.
 *
 * <p>Redis is fully mocked — no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
class TelegramSubscriberServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @SuppressWarnings("unchecked")
    @Mock
    private SetOperations<String, String> setOps;

    private TelegramSubscriberService subscriberService;

    @BeforeEach
    void setUp() {
        when(redis.opsForSet()).thenReturn(setOps);
        subscriberService = new TelegramSubscriberService(redis);
    }

    // ── addSubscriber ─────────────────────────────────────────────────────────

    @Test
    void addSubscriber_callsSadd() {
        subscriberService.addSubscriber("123");

        verify(setOps).add(TelegramSubscriberService.SUBSCRIBERS_KEY, "123");
    }

    // ── removeSubscriber ──────────────────────────────────────────────────────

    @Test
    void removeSubscriber_callsSrem() {
        subscriberService.removeSubscriber("123");

        verify(setOps).remove(TelegramSubscriberService.SUBSCRIBERS_KEY, (Object) "123");
    }

    // ── getSubscribers ────────────────────────────────────────────────────────

    @Test
    void getSubscribers_returnsSetFromRedis() {
        when(setOps.members(TelegramSubscriberService.SUBSCRIBERS_KEY))
                .thenReturn(Set.of("111", "222"));

        Set<String> result = subscriberService.getSubscribers();

        assertThat(result).containsExactlyInAnyOrder("111", "222");
    }

    @Test
    void getSubscribers_returnsEmptySet_whenRedisReturnsNull() {
        when(setOps.members(TelegramSubscriberService.SUBSCRIBERS_KEY)).thenReturn(null);

        Set<String> result = subscriberService.getSubscribers();

        assertThat(result).isEmpty();
    }

    @Test
    void getSubscribers_returnsEmptySet_whenRedisThrowsException() {
        when(setOps.members(TelegramSubscriberService.SUBSCRIBERS_KEY))
                .thenThrow(new RuntimeException("Redis unavailable"));

        Set<String> result = subscriberService.getSubscribers();

        assertThat(result).isEmpty();
    }
}
