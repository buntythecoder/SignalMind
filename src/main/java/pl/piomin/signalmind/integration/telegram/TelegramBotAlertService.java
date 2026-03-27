package pl.piomin.signalmind.integration.telegram;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Telegram alert implementation that calls the Bot API (SM-20 / SM-28).
 *
 * <p>Active only when {@code telegram.bot-token} is present in configuration.
 * Uses {@link TelegramDispatcherService} when Redis is available; falls back to a
 * direct HTTP call otherwise.
 *
 * <p>Recipients are the union of the static {@code telegram.chat-id} property and
 * all chat IDs registered via {@link TelegramSubscriberService}.
 *
 * <p>All exceptions are caught and logged — a failed alert must never crash the application.
 */
@Service
@ConditionalOnProperty(prefix = "telegram", name = "bot-token", matchIfMissing = false)
public class TelegramBotAlertService implements TelegramAlertService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotAlertService.class);

    private final TelegramProperties              props;
    private final Optional<TelegramDispatcherService>  dispatcher;
    private final Optional<TelegramSubscriberService>  subscriberService;
    private final RestClient                      restClient;

    public TelegramBotAlertService(TelegramProperties props,
                                   Optional<TelegramDispatcherService> dispatcher,
                                   Optional<TelegramSubscriberService> subscriberService) {
        this.props            = props;
        this.dispatcher       = dispatcher;
        this.subscriberService = subscriberService;
        this.restClient       = RestClient.create();
    }

    /**
     * Registers the webhook URL with Telegram on startup if {@code telegram.webhook-url} is set.
     * Failures are logged as warnings and do not prevent startup.
     */
    @PostConstruct
    public void registerWebhook() {
        String webhookUrl = props.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + props.getBotToken() + "/setWebhook";
            Map<String, String> body = Map.of("url", webhookUrl);
            restClient.post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[telegram] Webhook registered: {}", webhookUrl);
        } catch (Exception e) {
            log.warn("[telegram] Failed to register webhook '{}': {}", webhookUrl, e.getMessage());
        }
    }

    /**
     * Sends {@code message} to all configured recipients.
     *
     * <p>Recipients: the static chat ID from properties (if not blank) plus all
     * dynamic subscribers.  Each recipient is dispatched via the queue when available,
     * or via a direct HTTP call as fallback.
     *
     * @param message alert text (HTML)
     */
    @Override
    public void sendAlert(String message) {
        Set<String> recipients = new HashSet<>();

        String staticChatId = props.getChatId();
        if (staticChatId != null && !staticChatId.isBlank()) {
            recipients.add(staticChatId);
        }

        subscriberService
                .map(TelegramSubscriberService::getSubscribers)
                .ifPresent(recipients::addAll);

        if (recipients.isEmpty()) {
            log.warn("[telegram] sendAlert called but no recipients configured — message suppressed");
            return;
        }

        for (String chatId : recipients) {
            if (dispatcher.isPresent()) {
                dispatcher.get().enqueue(chatId, message);
            } else {
                sendDirect(chatId, message);
            }
        }
    }

    /**
     * Sends {@code message} with an inline keyboard to all configured recipients (SM-29).
     *
     * <p>Uses the dispatcher queue when available, falling back to a plain direct send
     * (without the keyboard) when Redis is absent.
     *
     * @param message          alert text (HTML)
     * @param replyMarkupJson  Telegram InlineKeyboardMarkup JSON string; may be null
     */
    @Override
    public void sendAlertWithKeyboard(String message, String replyMarkupJson) {
        Set<String> recipients = new HashSet<>();

        String staticChatId = props.getChatId();
        if (staticChatId != null && !staticChatId.isBlank()) {
            recipients.add(staticChatId);
        }

        subscriberService
                .map(TelegramSubscriberService::getSubscribers)
                .ifPresent(recipients::addAll);

        if (recipients.isEmpty()) {
            log.warn("[telegram] sendAlertWithKeyboard called but no recipients configured — message suppressed");
            return;
        }

        for (String chatId : recipients) {
            if (dispatcher.isPresent()) {
                dispatcher.get().enqueue(chatId, message, replyMarkupJson);
            } else {
                sendDirect(chatId, message);
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private void sendDirect(String chatId, String text) {
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
            log.info("[telegram] Direct alert sent to chatId={}", chatId);
        } catch (Exception e) {
            log.error("[telegram] Failed to send direct alert to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }
}
