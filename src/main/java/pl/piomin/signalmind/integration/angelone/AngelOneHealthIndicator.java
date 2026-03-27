package pl.piomin.signalmind.integration.angelone;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health check for Angel One SmartAPI connectivity.
 * Full connectivity logic will be implemented in SM-12.
 */
@Component("angelOne")
public class AngelOneHealthIndicator implements HealthIndicator {

    private final AngelOneConfig config;

    public AngelOneHealthIndicator(AngelOneConfig config) {
        this.config = config;
    }

    @Override
    public Health health() {
        boolean configured = config.apiKey() != null && !config.apiKey().isBlank()
                && config.clientId() != null && !config.clientId().isBlank();

        if (!configured) {
            return Health.down()
                    .withDetail("reason", "Angel One API credentials not configured")
                    .withDetail("hint", "Set ANGELONE_API_KEY and ANGELONE_CLIENT_ID in .env")
                    .build();
        }

        return Health.unknown()
                .withDetail("status", "credentials-configured")
                .withDetail("feedUrl", config.feedUrl())
                .withDetail("note", "Live connectivity check implemented in SM-12")
                .build();
    }
}
