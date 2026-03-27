package pl.piomin.signalmind.stock.service;

import pl.piomin.signalmind.stock.domain.Candle;

import java.time.LocalDate;
import java.util.List;

/**
 * SPI for historical OHLCV candle data sources.
 *
 * <p>Implement this interface to add a new data provider (e.g. Angel One historical,
 * NSE Bhavcopy, Yahoo Finance). Spring will discover all implementations via
 * component scan; the {@link CandleIngestionService} orchestrates them.
 *
 * <p>Each provider is responsible for:
 * <ul>
 *   <li>Rate-limiting its own API calls</li>
 *   <li>Parsing provider-specific date/time formats into UTC {@link java.time.Instant}</li>
 *   <li>Returning fully-constructed {@link Candle} objects (not yet persisted)</li>
 * </ul>
 */
public interface HistoricalDataProvider {

    /**
     * Short, unique provider identifier used for logging and selection.
     * Must be lowercase and hyphenated, e.g. {@code "icici-breeze"} or {@code "angel-one"}.
     */
    String providerName();

    /**
     * Fetches 1-minute OHLCV candles for the given stock and date range.
     *
     * <p>Implementations must not persist the returned candles; persistence is
     * handled by {@link CandleIngestionService}.
     *
     * @param symbol    NSE stock symbol (e.g. {@code "RELIANCE"})
     * @param startDate first calendar date (IST) to fetch, inclusive
     * @param endDate   last calendar date (IST) to fetch, inclusive
     * @return ordered list of transient {@link Candle} objects (may be empty)
     * @throws UnsupportedOperationException if this provider cannot supply data
     *                                       for the given symbol
     * @throws InterruptedException          if a rate-limit wait is interrupted
     */
    List<Candle> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate)
            throws InterruptedException;

    /**
     * Returns {@code true} if this provider can supply historical data for {@code symbol}.
     * Default implementation returns {@code true}; override to restrict supported instruments.
     */
    default boolean supports(String symbol) {
        return true;
    }
}
