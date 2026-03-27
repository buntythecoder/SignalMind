package pl.piomin.signalmind.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Telegram alert implementation that calls the Bot API (SM-20).
 *
 * <p>Active only when {@code telegram.bot.token} is present in configuration.
 * Uses Spring 6's {@link RestClient} for the HTTP call.
 *
 * <p>All exceptions are caught and logged — a failed alert must never crash the application.
 */
@Service
@ConditionalOnProperty(name = "telegram.bot.token", matchIfMissing = false)
public class TelegramBotAlertService implements TelegramAlertService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotAlertService.class);

    private final String token;
    private final String chatId;
    private final RestClient restClient;

    public TelegramBotAlertService(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.chat.id}") String chatId) {
        this.token = token;
        this.chatId = chatId;
        this.restClient = RestClient.create();
    }

    @Override
    public void sendAlert(String message) {
        try {
            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            Map<String, String> body = Map.of(
                    "chat_id", chatId,
                    "text", message,
                    "parse_mode", "HTML"
            );
            restClient.post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[telegram] Alert sent: {}", message);
        } catch (Exception e) {
            log.error("[telegram] Failed to send alert: {} — error: {}", message, e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
