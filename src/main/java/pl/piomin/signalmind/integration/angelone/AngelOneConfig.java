package pl.piomin.signalmind.integration.angelone;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from application.yml:
 * <pre>
 * angelone:
 *   api-key: ...
 *   client-id: ...
 *   mpin: ...
 *   totp-secret: ...
 *   feed-url: wss://smartapisocket.angelbroking.com/smart-stream
 * </pre>
 * NOTE: @NotBlank validation added in SM-12 when live credentials are required.
 */
@ConfigurationProperties(prefix = "angelone")
public record AngelOneConfig(
        String apiKey,
        String clientId,
        String mpin,
        String totpSecret,
        String feedUrl
) {
    public AngelOneConfig {
        if (feedUrl == null || feedUrl.isBlank()) {
            feedUrl = "wss://smartapisocket.angelbroking.com/smart-stream";
        }
    }
}
