package pl.piomin.signalmind.regime.detector;

import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.regime.indicator.OhlcBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SPI for individual market regime classifiers (SM-21).
 *
 * <p>Each implementation detects exactly one regime. Implementations are
 * registered as {@code @Component} beans and discovered via
 * {@code List<RegimeDetector>} injection in {@code MarketRegimeService}.
 * The service runs them in ascending {@link #priority()} order and returns
 * the first non-empty result.
 */
public interface RegimeDetector {

    /**
     * Attempt to classify the current market regime.
     *
     * @param bars          Nifty50 1-min OHLC bars, oldest first. At least 50 bars provided.
     * @param currentVix    Latest India VIX close price; {@code null} when data is stale
     *                      (older than 5 minutes) — implementors must fall back to ATR
     * @param vwap          Current session VWAP for Nifty50; may be {@code null} for
     *                      historical (HIST-source) candles
     * @param latestBarTime Timestamp of the most recent bar, used for CIRCUIT_HALT staleness checks
     * @return the detected regime, or {@link Optional#empty()} if this detector's
     *         conditions are not met
     */
    Optional<MarketRegime> detect(List<OhlcBar> bars,
                                  BigDecimal currentVix,
                                  BigDecimal vwap,
                                  Instant latestBarTime);

    /**
     * Detection priority — lower value is checked first.
     * CIRCUIT_HALT must be priority 0 so that it short-circuits all others.
     */
    int priority();

    /** Human-readable name used in {@link pl.piomin.signalmind.regime.domain.RegimeSnapshot#reason()}. */
    String detectorName();
}
