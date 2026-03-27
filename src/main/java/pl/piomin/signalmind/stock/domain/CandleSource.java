package pl.piomin.signalmind.stock.domain;

/**
 * Origin of a {@link Candle} record — must match the DB CHECK constraint
 * {@code chk_candles_source IN ('LIVE','HIST')}.
 */
public enum CandleSource {
    /** Seeded from ICICI Breeze historical API. */
    HIST,
    /** Received in real-time from Angel One WebSocket. */
    LIVE
}
