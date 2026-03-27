package pl.piomin.signalmind.signal.service;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.stock.domain.Stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * SM-26: Confidence Scoring Engine.
 *
 * <p>Computes a 6-factor confidence score for every generated signal and writes
 * the individual factor values back to the signal for audit/model-improvement.
 * The final {@code confidence} field is the clamped sum (max 100).
 *
 * <h2>Factor breakdown (max 100)</h2>
 * <ol>
 *   <li><strong>Base</strong> = 50 — always awarded; represents a neutral prior.</li>
 *   <li><strong>Volume</strong> = 0–15 — strength of the volume confirmation.</li>
 *   <li><strong>Time-of-day</strong> = 3–10 — IST session slot quality.</li>
 *   <li><strong>Regime</strong> = 0–10 — direction-regime alignment.</li>
 *   <li><strong>Win-rate</strong> = 6–8 — per-type historical win-rate proxy.</li>
 *   <li><strong>Confluence</strong> = 0–15 — multi-signal confluence bonus
 *       (only the 2nd/3rd signal in a 5-minute window earns this).</li>
 * </ol>
 */
@Component
public class ConfidenceScoringService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Confluence look-back window in minutes. */
    private static final int CONFLUENCE_WINDOW_MIN = 5;

    private final SignalRepository signalRepository;

    public ConfidenceScoringService(SignalRepository signalRepository) {
        this.signalRepository = signalRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scores a newly built (not yet persisted) signal and writes the breakdown
     * back via {@link Signal#applyScores}.
     *
     * <p>Call this <strong>after</strong> the detector constructs the signal but
     * <strong>before</strong> persisting it to the database.
     *
     * @param signal   the signal to score
     * @param volume   volume of the candle that triggered the signal
     * @param baseline historical average volume for that time slot (null = unknown)
     */
    public void score(Signal signal, long volume, Long baseline) {
        int base      = scoreBase();
        int vol       = scoreVolume(volume, baseline);
        int tod       = scoreTimeOfDay(signal.getGeneratedAt());
        int regime    = scoreRegime(signal.getDirection(), signal.getRegime());
        int winRate   = scoreWinRate(signal.getSignalType());
        int confluence = scoreConfluence(signal.getStock(), signal.getGeneratedAt());

        signal.applyScores(base, vol, tod, regime, winRate, confluence);
    }

    // ── Factor calculators (package-private for unit testing) ────────────────

    /**
     * Factor 1 — Base score: always 50 (neutral prior).
     */
    int scoreBase() {
        return 50;
    }

    /**
     * Factor 2 — Volume confirmation.
     *
     * <ul>
     *   <li>≥ 3.0× baseline → 15</li>
     *   <li>≥ 2.0× baseline → 10</li>
     *   <li>≥ 1.5× baseline → 5</li>
     *   <li>otherwise or no baseline → 0</li>
     * </ul>
     */
    int scoreVolume(long volume, Long baseline) {
        if (baseline == null || baseline == 0L) return 0;
        BigDecimal ratio = BigDecimal.valueOf(volume)
                .divide(BigDecimal.valueOf(baseline), 4, java.math.RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("3.0")) >= 0) return 15;
        if (ratio.compareTo(new BigDecimal("2.0")) >= 0) return 10;
        if (ratio.compareTo(new BigDecimal("1.5")) >= 0) return 5;
        return 0;
    }

    /**
     * Factor 3 — Time-of-day quality (IST).
     *
     * <ul>
     *   <li>09:15–09:30 (opening volatility window) → 10</li>
     *   <li>09:30–10:00 (high-activity morning) → 7</li>
     *   <li>10:00–11:00 (active continuation) → 5</li>
     *   <li>other (lunch/afternoon lull) → 3</li>
     * </ul>
     */
    int scoreTimeOfDay(Instant generatedAt) {
        LocalTime t = generatedAt.atZone(IST).toLocalTime();
        if (!t.isBefore(LocalTime.of(9, 15)) && t.isBefore(LocalTime.of(9, 30)))  return 10;
        if (!t.isBefore(LocalTime.of(9, 30)) && t.isBefore(LocalTime.of(10, 0)))  return 7;
        if (!t.isBefore(LocalTime.of(10, 0)) && t.isBefore(LocalTime.of(11, 0)))  return 5;
        return 3;
    }

    /**
     * Factor 4 — Regime alignment.
     *
     * <ul>
     *   <li>LONG + TRENDING_UP or SHORT + TRENDING_DOWN → 10</li>
     *   <li>SIDEWAYS or HIGH_VOLATILITY (direction-neutral) → 5</li>
     *   <li>direction against dominant trend → 0</li>
     * </ul>
     */
    int scoreRegime(SignalDirection direction, String regime) {
        if (regime == null) return 5;
        return switch (regime) {
            case "TRENDING_UP"   -> direction == SignalDirection.LONG  ? 10 : 0;
            case "TRENDING_DOWN" -> direction == SignalDirection.SHORT ? 10 : 0;
            case "SIDEWAYS", "HIGH_VOLATILITY" -> 5;
            default -> 0; // CIRCUIT_HALT or unknown
        };
    }

    /**
     * Factor 5 — Historical win-rate proxy (hardcoded per signal type).
     * These values are placeholder estimates; replace with live statistics once
     * sufficient backtesting data is accumulated.
     */
    int scoreWinRate(SignalType type) {
        return switch (type) {
            case ORB                     -> 8;
            case VWAP_BREAKOUT           -> 7;
            case VWAP_BREAKDOWN          -> 7;
            case RSI_OVERSOLD_BOUNCE     -> 6;
            case RSI_OVERBOUGHT_REJECTION -> 6;
            case GAP_FILL_LONG           -> 8;
            case GAP_FILL_SHORT          -> 7;
            default                      -> 6;
        };
    }

    /**
     * Factor 6 — Multi-signal confluence.
     *
     * <p>Queries signals persisted for the same stock within the last
     * {@value #CONFLUENCE_WINDOW_MIN} minutes.
     *
     * <ul>
     *   <li>0 prior signals → 0 (first signal; no confluence yet)</li>
     *   <li>1 prior signal → 10 (second signal confirms the move)</li>
     *   <li>2+ prior signals → 15 (three or more detectors agree)</li>
     * </ul>
     *
     * <p>Only the 2nd/3rd signal earns the bonus; the first signal never does.
     */
    int scoreConfluence(Stock stock, Instant generatedAt) {
        Instant windowStart = generatedAt.minus(CONFLUENCE_WINDOW_MIN, ChronoUnit.MINUTES);
        List<Signal> recent = signalRepository
                .findByStockAndGeneratedAtBetweenOrderByGeneratedAtAsc(stock, windowStart, generatedAt);
        if (recent.size() == 0) return 0;
        if (recent.size() == 1) return 10;
        return 15;
    }
}
