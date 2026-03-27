package pl.piomin.signalmind.regime.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Immutable snapshot of the current market regime classification (SM-21).
 *
 * <p>Serialised to JSON and stored in Redis under {@code regime:current}.
 */
public record RegimeSnapshot(
        @JsonProperty("regime")      MarketRegime regime,
        @JsonProperty("calculatedAt") Instant calculatedAt,
        @JsonProperty("reason")      String reason
) {}
