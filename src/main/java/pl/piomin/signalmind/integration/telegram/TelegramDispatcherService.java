package pl.piomin.signalmind.integration.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Redis-backed queue dispatcher for Telegram messages (SM-28).
 *
 * <p>Messages are enqueued into a Redis list and processed by a scheduled poller at up to
 * {@link #MAX_PER_SECOND} messages per second.  Failed sends are retried up to
 * {@link #MAX_ATTEMPTS} times with {@link #RETRY_BACKOFF_MS} ms delay; exhausted messages
 * are moved to the DLQ.  A separate scheduler re-enqueues DLQ items every 60 seconds.
 *
 * <p>Only active when both Redis ({@link StringRedisTemplate}) and a bot token are present.
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "telegram", name = "bot-token", matchIfMissing = false)
public class TelegramDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDispatcherService.class);

    public static final String QUEUE_KEY = "telegram:queue";
    public static final String DLQ_KEY   = "telegram:dlq";

    public static final int  MAX_ATTEMPTS    = 3;
    public static final long RETRY_BACKOFF_MS = 2_000L;
    public static final int  MAX_PER_SECOND  = 20;

    private final StringRedisTemplate redis;
    private final TelegramProperties  props;
    private final ObjectMapper        objectMapper;
    private final RestClient          restClient;

    // Rate-limit state — no concurrency concern because @Scheduled runs on a single thread
    private int  dispatchedThisSecond = 0;
    private long windowStartMs        = System.currentTimeMillis();

    public TelegramDispatcherService(StringRedisTemplate redis,
                                     TelegramProperties props,
                                     ObjectMapper objectMapper) {
        this.redis        = redis;
        this.props        = props;
        this.objectMapper = objectMapper;
        this.restClient   = RestClient.create();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Serialize {@code text} for {@code chatId} and RPUSH it onto the main queue.
     *
     * @param chatId Telegram chat ID (numeric string)
     * @param text   HTML-formatted message body
     */
    public void enqueue(String chatId, String text) {
        QueuedMessage msg = new QueuedMessage(chatId, text, 0, 0L);
        try {
            String json = objectMapper.writeValueAsString(msg);
            redis.opsForList().rightPush(QUEUE_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("[telegram-dispatcher] Failed to serialize message for chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    // ── Scheduled processors ───────────────────────────────────────────────────

    /**
     * Polls the main queue every 100 ms and dispatches one message per invocation,
     * subject to the rate limit of {@link #MAX_PER_SECOND}.
     */
    @Scheduled(fixedDelay = 100)
    public void processQueue() {
        String json = redis.opsForList().leftPop(QUEUE_KEY);
        if (json == null) {
            return;
        }

        QueuedMessage msg;
        try {
            msg = objectMapper.readValue(json, QueuedMessage.class);
        } catch (JsonProcessingException e) {
            log.error("[telegram-dispatcher] Unparseable message, discarding: {} — error: {}", json, e.getMessage());
            return;
        }

        // ── Rate limiting ──────────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (now - windowStartMs >= 1_000L) {
            dispatchedThisSecond = 0;
            windowStartMs = now;
        }
        if (dispatchedThisSecond >= MAX_PER_SECOND) {
            // Throttled — push back to end of queue and wait for next window
            safePushBack(QUEUE_KEY, true, json);
            return;
        }

        // ── Retry back-off ─────────────────────────────────────────────────────
        if (msg.retryAfterMs() > 0 && msg.retryAfterMs() > now) {
            safePushBack(QUEUE_KEY, true, json);
            return;
        }

        // ── Dispatch ───────────────────────────────────────────────────────────
        boolean success = sendToTelegram(msg.chatId(), msg.text());
        if (success) {
            dispatchedThisSecond++;
        } else {
            if (msg.attempts() < MAX_ATTEMPTS - 1) {
                // Retry with incremented attempt count, inserted at the front of the queue
                QueuedMessage retry = new QueuedMessage(
                        msg.chatId(), msg.text(),
                        msg.attempts() + 1,
                        System.currentTimeMillis() + RETRY_BACKOFF_MS);
                pushRetryToFront(retry);
            } else {
                // Exhausted — send to DLQ
                log.error("[telegram-dispatcher] Message for chatId={} exhausted {} attempts, moving to DLQ",
                        msg.chatId(), MAX_ATTEMPTS);
                safePushBack(DLQ_KEY, true, json);
            }
        }
    }

    /**
     * Re-enqueues all DLQ items onto the main queue (with attempts reset to 0) every 60 seconds.
     */
    @Scheduled(fixedDelay = 60_000)
    public void retryDlq() {
        String json;
        while ((json = redis.opsForList().leftPop(DLQ_KEY)) != null) {
            try {
                QueuedMessage dead = objectMapper.readValue(json, QueuedMessage.class);
                QueuedMessage fresh = new QueuedMessage(dead.chatId(), dead.text(), 0, 0L);
                String freshJson = objectMapper.writeValueAsString(fresh);
                redis.opsForList().rightPush(QUEUE_KEY, freshJson);
                log.info("[telegram-dispatcher] Re-enqueued DLQ message for chatId={}", dead.chatId());
            } catch (JsonProcessingException e) {
                log.error("[telegram-dispatcher] DLQ message unparseable, discarding: {}", json, e);
            }
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Calls the Telegram Bot API {@code sendMessage} endpoint.
     *
     * @param chatId recipient chat ID
     * @param text   HTML message body
     * @return {@code true} if the call succeeded, {@code false} on any error
     */
    boolean sendToTelegram(String chatId, String text) {
        try {
            String url = "https://api.telegram.org/bot" + props.getBotToken() + "/sendMessage";
            Map<String, String> body = Map.of(
                    "chat_id",    chatId,
                    "text",       text,
                    "parse_mode", "HTML"
            );
            restClient.post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("[telegram-dispatcher] Sent to chatId={}", chatId);
            return true;
        } catch (Exception e) {
            log.warn("[telegram-dispatcher] Send failed for chatId={}: {}", chatId, e.getMessage());
            return false;
        }
    }

    private void safePushBack(String key, boolean toRight, String json) {
        try {
            if (toRight) {
                redis.opsForList().rightPush(key, json);
            } else {
                redis.opsForList().leftPush(key, json);
            }
        } catch (Exception e) {
            log.error("[telegram-dispatcher] Redis push-back failed for key={}: {}", key, e.getMessage(), e);
        }
    }

    private void pushRetryToFront(QueuedMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            redis.opsForList().leftPush(QUEUE_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("[telegram-dispatcher] Failed to serialize retry message: {}", e.getMessage(), e);
        }
    }

    // ── Inner record ───────────────────────────────────────────────────────────

    /**
     * Immutable message envelope stored in the Redis queue.
     * Jackson deserializes Java records by matching field names.
     */
    public record QueuedMessage(
            @com.fasterxml.jackson.annotation.JsonProperty("chatId")       String chatId,
            @com.fasterxml.jackson.annotation.JsonProperty("text")         String text,
            @com.fasterxml.jackson.annotation.JsonProperty("attempts")     int    attempts,
            @com.fasterxml.jackson.annotation.JsonProperty("retryAfterMs") long   retryAfterMs
    ) {}
}
