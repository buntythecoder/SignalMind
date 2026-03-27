package pl.piomin.signalmind.candle.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Per-stock, per-session mutable accumulator for cumulative VWAP and its
 * standard-deviation bands.
 *
 * <h3>Algorithm</h3>
 * <ul>
 *   <li>Typical price (TP) = (high + low + close) / 3</li>
 *   <li>VWAP = Σ(TP × volume) / Σ(volume) — standard cumulative VWAP formula</li>
 *   <li>Variance is maintained online using <em>Welford's one-pass weighted method</em>
 *       to avoid the catastrophic cancellation that arises from the naïve
 *       (Σ(TP² × V) / ΣV) − VWAP² computation on large price series.</li>
 *   <li>Standard deviation = √(M2 / ΣV)</li>
 *   <li>Bands = VWAP ± 1 × std dev</li>
 * </ul>
 *
 * <p>Instances are <strong>not</strong> thread-safe; callers must ensure they
 * are accessed from a single thread (or hold an external lock). The assembler
 * uses a {@code ConcurrentHashMap} and never shares a state object between threads.
 *
 * <p>SM-19
 */
public class VwapState {

    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final int SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    // Σ(TP × volume)
    private BigDecimal cumulativeTPV = BigDecimal.ZERO;
    // Σ(volume)
    private long cumulativeVolume = 0;
    // Welford's M2 accumulator: Σ weight × (tp − old_mean) × (tp − new_mean)
    private BigDecimal m2 = BigDecimal.ZERO;

    /**
     * Incorporate a new 1-minute candle's data into the running VWAP and
     * variance state.
     *
     * @param high   candle high
     * @param low    candle low
     * @param close  candle close (last trade price)
     * @param volume candle volume — zero-volume candles are silently skipped
     */
    public void update(BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
        if (volume <= 0) {
            return;
        }
        BigDecimal tp = high.add(low).add(close).divide(THREE, SCALE, RM);
        BigDecimal w  = BigDecimal.valueOf(volume);

        BigDecimal oldVwap = rawVwap();

        cumulativeTPV    = cumulativeTPV.add(tp.multiply(w));
        cumulativeVolume += volume;

        BigDecimal newVwap = rawVwap();

        // Welford update: M2 += w × (tp − oldMean) × (tp − newMean)
        m2 = m2.add(w.multiply(tp.subtract(oldVwap)).multiply(tp.subtract(newVwap)));
    }

    /** VWAP rounded to 4 decimal places, suitable for storage. */
    public BigDecimal currentVwap() {
        return rawVwap().setScale(4, RM);
    }

    /** Upper band = VWAP + 1 × std dev, rounded to 4 decimal places. */
    public BigDecimal upperBand() {
        return rawVwap().add(stdDev()).setScale(4, RM);
    }

    /** Lower band = VWAP − 1 × std dev, rounded to 4 decimal places. */
    public BigDecimal lowerBand() {
        return rawVwap().subtract(stdDev()).setScale(4, RM);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Raw VWAP at full precision (SCALE digits). Returns ZERO before any updates. */
    private BigDecimal rawVwap() {
        if (cumulativeVolume == 0) {
            return BigDecimal.ZERO;
        }
        return cumulativeTPV.divide(BigDecimal.valueOf(cumulativeVolume), SCALE, RM);
    }

    /** Population standard deviation at full precision. Returns ZERO before any updates. */
    private BigDecimal stdDev() {
        if (cumulativeVolume == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal variance = m2.divide(BigDecimal.valueOf(cumulativeVolume), SCALE, RM);
        // Guard: variance can be slightly negative due to floating-point rounding;
        // clamp to zero before sqrt to avoid ArithmeticException.
        if (variance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return variance.sqrt(new MathContext(SCALE));
    }
}
