package pl.piomin.signalmind.integration.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the Telegram integration (SM-28).
 *
 * <p>Bound from the {@code telegram.*} namespace in {@code application.yml}.
 * Registered via {@link TelegramAlertConfiguration}.
 */
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    /** Bot API token from @BotFather. Empty string when not configured. */
    private String botToken = "";

    /** Default chat ID to send alerts to. Empty string when not configured. */
    private String chatId = "";

    /**
     * Optional HTTPS URL to register with Telegram as the webhook endpoint
     * (e.g. {@code https://your-domain.com/api/telegram/webhook}).
     * Leave blank to skip webhook registration on startup.
     */
    private String webhookUrl = "";

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
}
