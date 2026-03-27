package pl.piomin.signalmind.integration.telegram;

/**
 * SPI for sending operational alerts via Telegram (SM-20).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link TelegramBotAlertService} — active when {@code telegram.bot.token} is configured</li>
 *   <li>{@link NoOpTelegramAlertService} — fallback when the property is absent</li>
 * </ul>
 */
public interface TelegramAlertService {

    /**
     * Send an operational alert message.
     *
     * <p>Implementations must never propagate exceptions — alerts must not crash the application.
     *
     * @param message the alert text (plain text or HTML depending on implementation)
     */
    void sendAlert(String message);

    /**
     * Sends an alert with an inline keyboard attached (SM-29).
     *
     * <p>The default implementation delegates to {@link #sendAlert(String)}, discarding
     * the keyboard JSON.  Implementations that support inline keyboards should override
     * this method to pass {@code replyMarkupJson} to the dispatcher.
     *
     * @param message          the alert text (HTML)
     * @param replyMarkupJson  Telegram InlineKeyboardMarkup JSON string; may be null
     */
    default void sendAlertWithKeyboard(String message, String replyMarkupJson) {
        sendAlert(message);
    }

    /**
     * Returns {@code true} if a real notification channel is wired up and messages
     * will actually be delivered, {@code false} for the no-op fallback.
     */
    boolean isAvailable();
}
