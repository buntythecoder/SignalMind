package pl.piomin.signalmind.integration.angelone;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import pl.piomin.signalmind.ingestion.domain.ConnectionState;
import pl.piomin.signalmind.ingestion.service.DataIngestionService;

import java.util.Optional;

/**
 * Actuator health check for Angel One SmartAPI connectivity.
 *
 * <p>When {@link DataIngestionService} is active (i.e., {@code angelone.ingestion.enabled=true}),
 * reports the live WebSocket connection state for both Connection A (NIFTY50) and
 * Connection B (other indices). Both must be {@link ConnectionState#CONNECTED} for
 * the indicator to report {@code UP}.
 *
 * <p>When the ingestion service is absent (default — ingestion disabled), the indicator
 * falls back to credential-presence checking: {@code UNKNOWN} if credentials are
 * configured, {@code DOWN} if they are missing.
 */
@Component("angelOne")
public class AngelOneHealthIndicator implements HealthIndicator {

    private final AngelOneConfig config;
    private final Optional<DataIngestionService> ingestion;

    public AngelOneHealthIndicator(AngelOneConfig config,
                                   Optional<DataIngestionService> ingestion) {
        this.config = config;
        this.ingestion = ingestion;
    }

    @Override
    public Health health() {
        if (ingestion.isPresent()) {
            DataIngestionService svc = ingestion.get();
            boolean bothUp = svc.getStateA() == ConnectionState.CONNECTED
                    && svc.getStateB() == ConnectionState.CONNECTED;
            Health.Builder builder = bothUp ? Health.up() : Health.down();
            return builder
                    .withDetail("connA", svc.getStateA())
                    .withDetail("connB", svc.getStateB())
                    .withDetail("lastTickAt",
                            svc.getLastTickAt() != null ? svc.getLastTickAt().toString() : "none")
                    .build();
        }

        // Fallback: ingestion disabled — report credential configuration status.
        boolean configured = config.apiKey() != null && !config.apiKey().isBlank()
                && config.clientId() != null && !config.clientId().isBlank();
        return (configured ? Health.unknown() : Health.down())
                .withDetail("ingestionEnabled", false)
                .withDetail("feedUrl", config.feedUrl())
                .build();
    }
}
