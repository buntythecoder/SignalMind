package pl.piomin.signalmind.ingestion.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable snapshot of a single market quote received from the WebSocket feed.
 * Produced by {@code AngelOneWebSocketClient} and consumed by {@code TickBuffer} implementations.
 */
public record MarketTick(
        String symbol,
        Instant receivedAt,
        BigDecimal ltp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        String source
) {
}
