package pl.piomin.signalmind.integration.angelone;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
 * Hardened in SM-47: @NotBlank validation fails fast at startup when
 * credentials are missing, preventing silent runtime failures.
 */
@Validated
@ConfigurationProperties(prefix = "angelone")
public record AngelOneConfig(
        @NotBlank(message = "Angel One API key must not be blank (set ANGELONE_API_KEY)")
        String apiKey,
        @NotBlank(message = "Angel One client ID must not be blank (set ANGELONE_CLIENT_ID)")
        String clientId,
        @NotBlank(message = "Angel One MPIN must not be blank (set ANGELONE_MPIN)")
        String mpin,
        @NotBlank(message = "Angel One TOTP secret must not be blank (set ANGELONE_TOTP_SECRET)")
        String totpSecret,
        String feedUrl
) {
    public AngelOneConfig {
        if (feedUrl == null || feedUrl.isBlank()) {
            feedUrl = "wss://smartapisocket.angelbroking.com/smart-stream";
        }
    }
}
