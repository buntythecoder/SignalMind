package pl.piomin.signalmind.integration.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.signal.domain.SignalFeedback;
import pl.piomin.signalmind.signal.repository.SignalFeedbackRepository;

import java.util.Optional;

/**
 * Receives Telegram webhook updates and handles /start and /stop commands (SM-28)
 * and inline keyboard callback queries (SM-29).
 *
 * <p>The endpoint is publicly accessible (no auth required) so that Telegram's servers
 * can POST updates directly.  See {@code SecurityConfig} for the permit-all rule.
 *
 * <p>Both {@link TelegramDispatcherService} and {@link TelegramSubscriberService} are
 * injected as {@link Optional} so the controller loads cleanly in test profiles where
 * neither Redis nor a bot token is present.
 *
 * <p>{@link SignalFeedbackRepository} is injected with {@code @Autowired(required = false)}
 * to allow the controller to load cleanly when JPA is absent in test profiles.
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

    @Autowired(required = false)
    private SignalFeedbackRepository feedbackRepo;

    public TelegramWebhookController(Optional<TelegramDispatcherService> dispatcherService,
                                     Optional<TelegramSubscriberService> subscriberService) {
        this.dispatcherService = dispatcherService;
        this.subscriberService = subscriberService;
    }

    /**
     * Handles incoming Telegram updates.  Processes:
     * <ul>
     *   <li>{@code /start} and {@code /stop} text commands</li>
     *   <li>Inline keyboard callback queries ({@code TOOK_TRADE} and {@code WATCHING})</li>
     * </ul>
     * All other updates are silently acknowledged with HTTP 200.
     *
     * @param update the deserialized Telegram update payload
     * @return HTTP 200 OK in all cases (Telegram requires a 200 to stop retrying)
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody TelegramUpdate update) {
        // Handle callback queries (inline keyboard button presses)
        if (update.callbackQuery() != null) {
            handleCallbackQuery(update.callbackQuery());
            return ResponseEntity.ok().build();
        }

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

    // ── Private helpers ───────────────────────────────────────────────────────

    private void handleCallbackQuery(TelegramCallbackQuery callbackQuery) {
        String data = callbackQuery.data();
        if (data == null || !data.contains(":")) {
            log.debug("[telegram-webhook] Ignoring callback with unexpected data='{}'", data);
            dispatcherService.ifPresent(d -> d.answerCallbackQuery(callbackQuery.id()));
            return;
        }

        int colonIdx = data.indexOf(':');
        String type     = data.substring(0, colonIdx);
        String signalIdStr = data.substring(colonIdx + 1);

        if (!"TOOK_TRADE".equals(type) && !"WATCHING".equals(type)) {
            log.debug("[telegram-webhook] Ignoring unknown callback type='{}'", type);
            dispatcherService.ifPresent(d -> d.answerCallbackQuery(callbackQuery.id()));
            return;
        }

        String chatId = String.valueOf(callbackQuery.from().id());
        log.info("[telegram-webhook] Callback type={} signalId={} from chatId={}", type, signalIdStr, chatId);

        if (feedbackRepo != null) {
            try {
                long signalId = Long.parseLong(signalIdStr);
                feedbackRepo.save(new SignalFeedback(signalId, chatId, type));
                log.info("[telegram-webhook] Saved feedback type={} signalId={} chatId={}", type, signalId, chatId);
            } catch (Exception e) {
                // Unique constraint violation (duplicate feedback) or parse error — silently ignore
                log.warn("[telegram-webhook] Could not save feedback for signalId={} chatId={}: {}",
                        signalIdStr, chatId, e.getMessage());
            }
        }

        dispatcherService.ifPresent(d -> d.answerCallbackQuery(callbackQuery.id()));
    }

    // ── Inner records ──────────────────────────────────────────────────────────

    /**
     * Top-level Telegram Update object.
     *
     * @param updateId      unique monotonically increasing identifier for the update
     * @param message       the message payload; may be null for non-message updates
     * @param callbackQuery the callback query payload; non-null when a user taps an inline button
     */
    public record TelegramUpdate(
            @JsonProperty("update_id")      long updateId,
            TelegramMessage                 message,
            @JsonProperty("callback_query") TelegramCallbackQuery callbackQuery
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

    /**
     * Telegram CallbackQuery object — produced when a user taps an inline keyboard button.
     *
     * @param id   unique identifier for this callback query (used to answer it)
     * @param from the user who pressed the button
     * @param data the callback data string associated with the pressed button
     */
    public record TelegramCallbackQuery(
            String id,
            TelegramUser from,
            String data
    ) {}

    /**
     * Abbreviated Telegram User object.
     *
     * @param id unique identifier for the user
     */
    public record TelegramUser(long id) {}
}
