package pl.piomin.signalmind.integration.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Receives Telegram webhook updates and handles /start and /stop commands (SM-28).
 *
 * <p>The endpoint is publicly accessible (no auth required) so that Telegram's servers
 * can POST updates directly.  See {@code SecurityConfig} for the permit-all rule.
 *
 * <p>Both {@link TelegramDispatcherService} and {@link TelegramSubscriberService} are
 * injected as {@link Optional} so the controller loads cleanly in test profiles where
 * neither Redis nor a bot token is present.
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private static final String WELCOME_MSG =
            "\u2705 Welcome to SignalMind! You will now receive intraday signals during market hours. " +
            "Send /stop to unsubscribe.";

    private static final String GOODBYE_MSG =
            "\uD83D\uDC4B You have been unsubscribed from SignalMind alerts. " +
            "Send /start at any time to re-subscribe.";

    private final Optional<TelegramDispatcherService>  dispatcherService;
    private final Optional<TelegramSubscriberService>  subscriberService;

    public TelegramWebhookController(Optional<TelegramDispatcherService> dispatcherService,
                                     Optional<TelegramSubscriberService> subscriberService) {
        this.dispatcherService = dispatcherService;
        this.subscriberService = subscriberService;
    }

    /**
     * Handles incoming Telegram updates.  Only text messages containing {@code /start} or
     * {@code /stop} are acted upon; all others are silently acknowledged with HTTP 200.
     *
     * @param update the deserialized Telegram update payload
     * @return HTTP 200 OK in all cases (Telegram requires a 200 to stop retrying)
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody TelegramUpdate update) {
        if (update.message() == null || update.message().text() == null) {
            return ResponseEntity.ok().build();
        }

        String text   = update.message().text().trim();
        String chatId = String.valueOf(update.message().chat().id());

        if ("/start".equals(text)) {
            log.info("[telegram-webhook] /start from chatId={}", chatId);
            subscriberService.ifPresent(svc -> svc.addSubscriber(chatId));
            dispatcherService.ifPresent(svc -> svc.enqueue(chatId, WELCOME_MSG));
        } else if ("/stop".equals(text)) {
            log.info("[telegram-webhook] /stop from chatId={}", chatId);
            subscriberService.ifPresent(svc -> svc.removeSubscriber(chatId));
            dispatcherService.ifPresent(svc -> svc.enqueue(chatId, GOODBYE_MSG));
        } else {
            log.debug("[telegram-webhook] Ignored text '{}' from chatId={}", text, chatId);
        }

        return ResponseEntity.ok().build();
    }

    // ── Inner records ──────────────────────────────────────────────────────────

    /**
     * Top-level Telegram Update object.
     *
     * @param updateId unique monotonically increasing identifier for the update
     * @param message  the message payload; may be null for non-message updates
     */
    public record TelegramUpdate(
            @JsonProperty("update_id") long updateId,
            TelegramMessage message
    ) {}

    /**
     * Telegram Message object (abbreviated — only fields we care about).
     *
     * @param messageId unique message identifier
     * @param chat      the chat this message belongs to
     * @param text      message text; null for non-text messages
     */
    public record TelegramMessage(
            @JsonProperty("message_id") long messageId,
            TelegramChat chat,
            String text
    ) {}

    /**
     * Telegram Chat object.
     *
     * @param id unique identifier for the chat
     */
    public record TelegramChat(long id) {}
}
