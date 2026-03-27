package pl.piomin.signalmind.ingestion.service;

import pl.piomin.signalmind.ingestion.domain.MarketTick;

/**
 * SPI for downstream consumers of market ticks.
 *
 * <p>Implementations are discovered via Spring's {@code List<TickBuffer>} auto-injection.
 * Each implementation decides independently whether to accept a given symbol
 * via {@link #supports(String)}.
 *
 * <p>Current implementations:
 * <ul>
 *   <li>{@code RedisTickBuffer} — writes latest tick to Redis with a 2-minute TTL</li>
 * </ul>
 */
public interface TickBuffer {

    /**
     * A stable, unique name for this buffer (used in logs and metrics).
     */
    String bufferName();

    /**
     * Publish a tick to the underlying store.
     * Implementations must not throw checked exceptions; swallow and log on failure.
     */
    void publish(MarketTick tick);

    /**
     * Returns {@code true} if this buffer should receive ticks for the given symbol.
     * Default implementation accepts all symbols.
     */
    default boolean supports(String symbol) {
        return true;
    }
}
