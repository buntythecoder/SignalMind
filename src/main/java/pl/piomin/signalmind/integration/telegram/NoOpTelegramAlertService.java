package pl.piomin.signalmind.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback no-op implementation of {@link TelegramAlertService} (SM-20).
 *
 * <p>Registered by {@link TelegramAlertConfiguration} when no real
 * {@link TelegramAlertService} bean is present (i.e. {@code telegram.bot.token}
 * is not configured). Logs a warning so operators are aware the alert was suppressed.
 */
public class NoOpTelegramAlertService implements TelegramAlertService {

    private static final Logger log = LoggerFactory.getLogger(NoOpTelegramAlertService.class);

    @Override
    public void sendAlert(String message) {
        log.warn("[telegram] No Telegram bot configured — alert suppressed: {}", message);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
