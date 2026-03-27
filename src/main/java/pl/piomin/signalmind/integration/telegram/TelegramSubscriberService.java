package pl.piomin.signalmind.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * Manages the set of Telegram chat IDs that have subscribed to SignalMind alerts (SM-28).
 *
 * <p>Subscriber state is persisted in a Redis set under {@link #SUBSCRIBERS_KEY}.
 * Only active when both Redis ({@link StringRedisTemplate}) and a bot token are configured.
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "telegram", name = "bot-token", matchIfMissing = false)
public class TelegramSubscriberService {

    private static final Logger log = LoggerFactory.getLogger(TelegramSubscriberService.class);

    public static final String SUBSCRIBERS_KEY = "telegram:subscribers";

    private final StringRedisTemplate redis;

    public TelegramSubscriberService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Adds {@code chatId} to the subscriber set.
     *
     * @param chatId Telegram chat ID (numeric string)
     */
    public void addSubscriber(String chatId) {
        redis.opsForSet().add(SUBSCRIBERS_KEY, chatId);
        log.info("[telegram-subscribers] Added subscriber: {}", chatId);
    }

    /**
     * Removes {@code chatId} from the subscriber set.
     *
     * @param chatId Telegram chat ID (numeric string)
     */
    public void removeSubscriber(String chatId) {
        redis.opsForSet().remove(SUBSCRIBERS_KEY, (Object) chatId);
        log.info("[telegram-subscribers] Removed subscriber: {}", chatId);
    }

    /**
     * Returns all currently subscribed chat IDs.
     *
     * @return set of chat ID strings; empty set on null or Redis error
     */
    public Set<String> getSubscribers() {
        try {
            Set<String> members = redis.opsForSet().members(SUBSCRIBERS_KEY);
            return members != null ? members : Collections.emptySet();
        } catch (Exception e) {
            log.error("[telegram-subscribers] Failed to fetch subscribers: {}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }
}
