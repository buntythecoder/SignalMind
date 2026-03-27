package pl.piomin.signalmind.signal.detector;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Opening-Range Breakout (ORB) signal detector (SM-22).
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Identify the <em>Opening Range</em> (OR): candles from 09:15 to 09:29 IST (inclusive
 *       of 09:15, exclusive of 09:30). Synthetic candles are included for range calculation
 *       because the OR high/low must reflect the true extremes of the period.</li>
 *   <li>Reject the setup if: no OR candles exist, ORH == ORL (flat), or the
 *       OR width exceeds {@value #ORW_MAX_PCT_STRING}% of mid-price (too volatile to trade cleanly).</li>
 *   <li>Scan breakout-window candles (09:30–11:30 IST, real candles only, oldest first).
 *       The first candle whose close breaks out of the OR with sufficient volume confirmation
 *       triggers a signal. Only the <em>first</em> qualifying breakout is returned.</li>
 *   <li>Price levels: entry = ORH (LONG) or ORL (SHORT); T1 = entry ± risk;
 *       T2 = entry ± 2×risk; stop = ORL (LONG) or ORH (SHORT), where risk = ORH − ORL.</li>
 *   <li>Reject if T2 R:R &lt; 2 (degenerate setup after rounding).</li>
 *   <li>Confidence: 50 + {@link MarketRegime#confidenceModifier()}, clamped to [0, 100].</li>
 *   <li>Signal validity window: 15 minutes from the breakout candle's timestamp.</li>
 * </ol>
 *
 * <p>This class is <strong>stateless</strong> — all state lives in local variables inside
 * {@link #detect}. It is safe for concurrent use without synchronisation.
 */
@Component
public class OrbSignalDetector implements SignalDetector {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Opening-range window: [09:15, 09:30)
    private static final LocalTime OR_START  = LocalTime.of(9, 15);
    private static final LocalTime OR_END    = LocalTime.of(9, 30);   // exclusive

    // Breakout window: [09:30, 11:30]
    private static final LocalTime ORB_START = LocalTime.of(9, 30);   // inclusive
    private static final LocalTime ORB_END   = LocalTime.of(11, 30);  // inclusive

    private static final String ORW_MAX_PCT_STRING = "2.5";
    private static final BigDecimal ORW_MAX_PCT  = new BigDecimal(ORW_MAX_PCT_STRING);
    private static final BigDecimal VOLUME_MULT  = new BigDecimal("1.5");
    private static final BigDecimal TWO          = new BigDecimal("2");
    private static final BigDecimal HUNDRED      = new BigDecimal("100");

    // ── SignalDetector SPI ────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Expects {@code todayCandles} sorted oldest-first (as assembled by
     * {@link pl.piomin.signalmind.signal.service.SignalEngineService}).
     */
    @Override
    public Optional<Signal> detect(Stock stock, List<Candle> todayCandles,
                                    Map<LocalTime, Long> volumeBaselines,
                                    String regime) {

        // ── Step 1: Partition candles by role ─────────────────────────────────
        //
        // OR candles: all candles (including synthetics) whose IST slot falls in [09:15, 09:30).
        // Synthetic candles ARE included here because the OR high/low must capture the full
        // price range of the opening period — if a flat (synthetic) candle sits between two
        // real candles its OHLC = prevClose contributes to the range correctly.
        List<Candle> orCandles = todayCandles.stream()
                .filter(c -> {
                    LocalTime t = c.getCandleTime().atZone(IST).toLocalTime();
                    return !t.isBefore(OR_START) && t.isBefore(OR_END);
                })
                .toList();

        // ── Step 2: Guard — must have at least one OR candle ──────────────────
        if (orCandles.isEmpty()) {
            return Optional.empty();
        }

        // ── Step 3: Compute opening-range high and low ────────────────────────
        BigDecimal orh = orCandles.stream()
                .map(Candle::getHigh)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal orl = orCandles.stream()
                .map(Candle::getLow)
                .min(BigDecimal::compareTo)
                .orElseThrow();

        // ── Step 4: Reject flat range ─────────────────────────────────────────
        if (orh.compareTo(orl) == 0) {
            return Optional.empty();
        }

        // ── Step 5: Reject if OR width % > ORW_MAX_PCT ───────────────────────
        //
        // ORW% = (ORH − ORL) / midPrice × 100
        // A very wide opening range (> 2.5%) indicates too much uncertainty to predict
        // a clean breakout direction.
        BigDecimal midPrice = orh.add(orl).divide(TWO, 4, RoundingMode.HALF_UP);
        BigDecimal orbWidthPct = orh.subtract(orl)
                .divide(midPrice, 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
        if (orbWidthPct.compareTo(ORW_MAX_PCT) > 0) {
            return Optional.empty();
        }

        // ── Step 6: Collect breakout-window candles (real only, sorted oldest-first) ──
        //
        // Synthetic candles are explicitly excluded from breakout detection: a synthetic
        // candle carries OHLC = prevClose and volume = 0, so it can never represent a
        // genuine price breakout with volume confirmation.
        List<Candle> breakoutCandles = todayCandles.stream()
                .filter(c -> !c.isSynthetic())
                .filter(c -> {
                    LocalTime t = c.getCandleTime().atZone(IST).toLocalTime();
                    return !t.isBefore(ORB_START) && !t.isAfter(ORB_END);
                })
                .sorted(Comparator.comparing(Candle::getCandleTime))
                .toList();

        // ── Step 7: Scan for the first qualifying breakout ────────────────────
        BigDecimal risk = orh.subtract(orl).setScale(2, RoundingMode.HALF_UP);
        int regimeModifier = parseRegimeModifier(regime);

        for (Candle c : breakoutCandles) {
            LocalTime slot = c.getCandleTime().atZone(IST).toLocalTime();
            Long baseline = volumeBaselines.get(slot);

            boolean bullish = c.getClose().compareTo(orh) > 0 && volumeOk(c.getVolume(), baseline);
            boolean bearish = c.getClose().compareTo(orl) < 0 && volumeOk(c.getVolume(), baseline);

            if (!bullish && !bearish) {
                continue;
            }

            SignalDirection direction = bullish ? SignalDirection.LONG : SignalDirection.SHORT;

            BigDecimal entry;
            BigDecimal t1;
            BigDecimal t2;
            BigDecimal stopLoss;

            if (bullish) {
                entry    = orh.setScale(2, RoundingMode.HALF_UP);
                t1       = entry.add(risk);
                t2       = entry.add(risk.multiply(TWO));
                stopLoss = orl.setScale(2, RoundingMode.HALF_UP);
            } else {
                entry    = orl.setScale(2, RoundingMode.HALF_UP);
                t1       = entry.subtract(risk);
                t2       = entry.subtract(risk.multiply(TWO));
                stopLoss = orh.setScale(2, RoundingMode.HALF_UP);
            }

            // ── Step 8: R:R check — reward(T2) / risk must be >= 2 ───────────
            BigDecimal rewardT2 = bullish ? t2.subtract(entry) : entry.subtract(t2);
            if (risk.compareTo(BigDecimal.ZERO) <= 0
                    || rewardT2.divide(risk, 4, RoundingMode.HALF_UP).compareTo(TWO) < 0) {
                continue;
            }

            // ── Step 9: Compute confidence ────────────────────────────────────
            int rawConfidence = 50 + regimeModifier;
            int confidence = Math.max(0, Math.min(100, rawConfidence));

            // ── Step 10: Validity window = breakout candle time + 15 min ─────
            Instant generatedAt = c.getCandleTime();
            Instant validUntil  = generatedAt.plus(15, ChronoUnit.MINUTES);

            Signal signal = new Signal(
                    stock, SignalType.ORB, direction,
                    entry, t1, t2, stopLoss,
                    confidence, regime != null ? regime : "SIDEWAYS",
                    generatedAt, validUntil,
                    orh.setScale(2, RoundingMode.HALF_UP),
                    orl.setScale(2, RoundingMode.HALF_UP)
            );
            return Optional.of(signal);
        }

        return Optional.empty();
    }

    @Override
    public String detectorName() {
        return "OrbSignalDetector";
    }

    @Override
    public SignalType signalType() {
        return SignalType.ORB;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} when volume meets or exceeds 1.5× the slot baseline.
     * If no baseline is available (null or zero), volume check is skipped and {@code true} is returned
     * to avoid rejecting legitimate signals due to missing baseline data.
     */
    private boolean volumeOk(long volume, Long baseline) {
        if (baseline == null || baseline == 0L) {
            return true;
        }
        return BigDecimal.valueOf(volume)
                .compareTo(BigDecimal.valueOf(baseline).multiply(VOLUME_MULT)) >= 0;
    }

    /**
     * Maps a regime name string to its {@link MarketRegime#confidenceModifier()}.
     * Returns 0 for null or unrecognised values instead of propagating an exception.
     */
    private int parseRegimeModifier(String regime) {
        if (regime == null) {
            return 0;
        }
        try {
            return MarketRegime.valueOf(regime).confidenceModifier();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
