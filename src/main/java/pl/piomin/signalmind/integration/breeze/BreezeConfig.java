package pl.piomin.signalmind.integration.breeze;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from application.yml:
 * <pre>
 * breeze:
 *   api-key: ...
 *   api-secret: ...
 *   session-token: ...
 * </pre>
 */
@ConfigurationProperties(prefix = "breeze")
@Validated
public record BreezeConfig(
        String apiKey,
        String apiSecret,
        String sessionToken
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }
}
