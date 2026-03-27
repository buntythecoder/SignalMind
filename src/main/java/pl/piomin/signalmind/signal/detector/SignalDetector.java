package pl.piomin.signalmind.signal.detector;

import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SPI for trading-signal detectors (SM-22 and future stories).
 *
 * <p>Each implementation is a stateless Spring bean discovered automatically
 * via {@code List<SignalDetector>} injection in
 * {@link pl.piomin.signalmind.signal.service.SignalEngineService}.
 *
 * <p>Contract:
 * <ul>
 *   <li>Implementations must be <strong>stateless</strong> — no mutable instance fields.</li>
 *   <li>{@code detect} must never throw; return {@link Optional#empty()} on invalid input.</li>
 *   <li>Returned {@link Signal} objects are unsaved; persistence is the caller's responsibility.</li>
 * </ul>
 */
public interface SignalDetector {

    /**
     * Analyses today's candles and emits a signal if all entry conditions are met.
     *
     * @param stock           the instrument being evaluated
     * @param todayCandles    all candles for today's session, ordered <em>oldest first</em>
     * @param volumeBaselines map of IST slot time → historical average volume for that slot
     * @param regime          current market regime name (e.g. "TRENDING_UP"); may be null
     * @return the generated signal, or {@link Optional#empty()} if conditions are not met
     */
    Optional<Signal> detect(Stock stock, List<Candle> todayCandles,
                             Map<LocalTime, Long> volumeBaselines,
                             String regime);

    /**
     * Human-readable name used in log messages and metrics tags.
     *
     * @return detector name, e.g. {@code "OrbSignalDetector"}
     */
    String detectorName();

    /**
     * The {@link SignalType} this detector produces.
     * Used by the engine to check for duplicate signals per session.
     *
     * @return signal type constant
     */
    SignalType signalType();

    /**
     * Maximum number of signals this detector may generate per stock per session.
     * Default is 1 (one signal per stock per day). Override for detectors that
     * allow multiple signals per session (e.g. VWAP allows up to 3).
     *
     * @return maximum signals per stock per session
     */
    default int maxSignalsPerDay() {
        return 1;
    }

    /**
     * The set of signal types to include when counting signals against
     * {@link #maxSignalsPerDay()}. Default is just this detector's own type.
     * Override when a daily cap is shared across multiple types
     * (e.g. VWAP_BREAKOUT and VWAP_BREAKDOWN share a combined cap of 3).
     *
     * @return signal types whose count is checked against the cap
     */
    default List<SignalType> countedTypes() {
        return List.of(signalType());
    }
}
