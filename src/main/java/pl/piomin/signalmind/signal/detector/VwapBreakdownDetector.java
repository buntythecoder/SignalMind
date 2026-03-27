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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detects VWAP Breakdown signals (SM-23).
 *
 * <p>Six conditions must ALL be satisfied on the current candle:
 * <ol>
 *   <li>Previous close was above VWAP (price was over VWAP)</li>
 *   <li>Current close is below VWAP (price crossed below VWAP)</li>
 *   <li>Volume &ge; 2.0&times; time-slot baseline (institutional conviction)</li>
 *   <li>RSI(14) between 30 and 60 (not overbought, not deeply oversold)</li>
 *   <li>Close is within 0.5% of VWAP (entry is close to VWAP)</li>
 *   <li>Market regime is NOT {@code TRENDING_UP}</li>
 * </ol>
 *
 * <p>Shares a combined daily cap of 3 signals with {@link VwapBreakoutDetector}
 * (VWAP_BREAKOUT + VWAP_BREAKDOWN combined per stock per session).
 */
@Component
public class VwapBreakdownDetector implements SignalDetector {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final BigDecimal VOLUME_MULT    = new BigDecimal("2.0");
    private static final BigDecimal RSI_MIN        = new BigDecimal("30");
    private static final BigDecimal RSI_MAX        = new BigDecimal("60");
    private static final BigDecimal VWAP_PROX_PCT  = new BigDecimal("0.5");
    private static final BigDecimal HUNDRED        = new BigDecimal("100");
    private static final BigDecimal ONE_POINT_FIVE = new BigDecimal("1.5");
    private static final BigDecimal TWO            = new BigDecimal("2");

    /** Combined VWAP signal cap (breakout + breakdown) per stock per session. */
    private static final int MAX_VWAP_SIGNALS_PER_DAY = 3;

    @Override
    public Optional<Signal> detect(Stock stock, List<Candle> todayCandles,
                                    Map<LocalTime, Long> volumeBaselines,
                                    String regime) {
        // Need at least 2 candles: a previous and a current
        if (todayCandles.size() < 2) {
            return Optional.empty();
        }

        // Condition 6: regime must NOT be TRENDING_UP
        if ("TRENDING_UP".equals(regime)) {
            return Optional.empty();
        }

        // Scan candles oldest-first; check each pair (prev, curr)
        for (int i = 1; i < todayCandles.size(); i++) {
            Candle prev = todayCandles.get(i - 1);
            Candle curr = todayCandles.get(i);

            // Skip synthetic candles — no real tick, cannot confirm breakdown
            if (curr.isSynthetic() || prev.isSynthetic()) continue;

            // VWAP and RSI are required on both current and previous candles
            BigDecimal prevVwap = prev.getVwap();
            BigDecimal currVwap = curr.getVwap();
            BigDecimal currRsi  = curr.getRsi();
            if (prevVwap == null || currVwap == null || currRsi == null) continue;

            BigDecimal prevClose = prev.getClose();
            BigDecimal currClose = curr.getClose();

            // Condition 1: prev close was above VWAP
            if (prevClose.compareTo(prevVwap) <= 0) continue;

            // Condition 2: curr close is below VWAP
            if (currClose.compareTo(currVwap) >= 0) continue;

            // Condition 3: volume >= 2.0x baseline
            LocalTime slot = curr.getCandleTime().atZone(IST).toLocalTime();
            Long baseline = volumeBaselines.get(slot);
            if (!volumeOk(curr.getVolume(), baseline)) continue;

            // Condition 4: RSI between 30 and 60
            if (currRsi.compareTo(RSI_MIN) < 0 || currRsi.compareTo(RSI_MAX) > 0) continue;

            // Condition 5: close within 0.5% of VWAP
            BigDecimal proxPct = currClose.subtract(currVwap).abs()
                    .divide(currVwap, 6, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            if (proxPct.compareTo(VWAP_PROX_PCT) > 0) continue;

            // All 6 conditions met — build signal
            Optional<Signal> signal = buildSignal(stock, curr, currVwap, regime);
            if (signal.isPresent()) return signal;
        }
        return Optional.empty();
    }

    private Optional<Signal> buildSignal(Stock stock, Candle curr, BigDecimal vwap, String regime) {
        BigDecimal entry = curr.getClose().setScale(2, RoundingMode.HALF_UP);

        // SL = vwapUpper (if available) else VWAP × (1 + 0.5%)
        BigDecimal stopLoss = curr.getVwapUpper() != null
                ? curr.getVwapUpper().setScale(2, RoundingMode.HALF_UP)
                : vwap.multiply(BigDecimal.ONE.add(VWAP_PROX_PCT.divide(HUNDRED, 6, RoundingMode.HALF_UP)))
                      .setScale(2, RoundingMode.HALF_UP);

        BigDecimal risk = stopLoss.subtract(entry);
        if (risk.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        BigDecimal t1 = entry.subtract(risk.multiply(ONE_POINT_FIVE)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal t2 = curr.getVwapLower() != null
                ? curr.getVwapLower().setScale(2, RoundingMode.HALF_UP)
                : entry.subtract(risk.multiply(TWO)).setScale(2, RoundingMode.HALF_UP);

        int regimeModifier = regime != null ? parseModifier(regime) : 0;
        int confidence = Math.max(0, Math.min(100, 50 + regimeModifier));

        Signal signal = new Signal(
                stock, SignalType.VWAP_BREAKDOWN, SignalDirection.SHORT,
                entry, t1, t2, stopLoss,
                confidence, regime != null ? regime : "SIDEWAYS",
                curr.getCandleTime(),
                curr.getCandleTime().plus(30, ChronoUnit.MINUTES),
                null, null   // orbHigh / orbLow not applicable
        );
        return Optional.of(signal);
    }

    private boolean volumeOk(long volume, Long baseline) {
        if (baseline == null || baseline == 0L) return true;
        return BigDecimal.valueOf(volume)
                .compareTo(BigDecimal.valueOf(baseline).multiply(VOLUME_MULT)) >= 0;
    }

    private int parseModifier(String regime) {
        try {
            return MarketRegime.valueOf(regime).confidenceModifier();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    @Override public String detectorName() { return "VwapBreakdownDetector"; }
    @Override public SignalType signalType() { return SignalType.VWAP_BREAKDOWN; }

    @Override
    public int maxSignalsPerDay() {
        return MAX_VWAP_SIGNALS_PER_DAY;
    }

    @Override
    public List<SignalType> countedTypes() {
        return List.of(SignalType.VWAP_BREAKOUT, SignalType.VWAP_BREAKDOWN);
    }
}
