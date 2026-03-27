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
 * Detects RSI Oversold Bounce signals (SM-24).
 *
 * <p>Five conditions must ALL be satisfied:
 * <ol>
 *   <li>Previous candle RSI was below 25 (oversold extreme)</li>
 *   <li>Current candle RSI has crossed back above 25 AND is rising (curr &gt; prev RSI)</li>
 *   <li>Current close is within 1% of VWAP</li>
 *   <li>Volume &ge; 1.5&times; time-slot baseline</li>
 *   <li>Market regime is NOT {@code TRENDING_DOWN}</li>
 * </ol>
 *
 * <p>Additional constraints:
 * <ul>
 *   <li>Only signals generated between 10:00 and 14:30 IST are valid</li>
 *   <li>R:R (T1−entry) / (entry−SL) must be &ge; 1.5</li>
 *   <li>SL = previous candle low (the reversal candle)</li>
 *   <li>T1 = VWAP; T2 = entry + 2&times;risk (fallback)</li>
 * </ul>
 *
 * <p>Maximum 2 signals per stock per session; cap is direction-specific
 * (does not combine with {@link RsiOverboughtRejectionDetector}).
 */
@Component
public class RsiOversoldBounceDetector implements SignalDetector {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final LocalTime WINDOW_START = LocalTime.of(10, 0);
    private static final LocalTime WINDOW_END   = LocalTime.of(14, 30);

    private static final BigDecimal RSI_THRESHOLD  = new BigDecimal("25");
    private static final BigDecimal VWAP_PROX_PCT  = new BigDecimal("1.0");
    private static final BigDecimal VOLUME_MULT    = new BigDecimal("1.5");
    private static final BigDecimal HUNDRED        = new BigDecimal("100");
    private static final BigDecimal MIN_RR         = new BigDecimal("1.5");
    private static final BigDecimal TWO            = new BigDecimal("2");

    private static final int MAX_SIGNALS_PER_DAY = 2;

    @Override
    public Optional<Signal> detect(Stock stock, List<Candle> todayCandles,
                                    Map<LocalTime, Long> volumeBaselines,
                                    String regime) {
        if (todayCandles.size() < 2) {
            return Optional.empty();
        }

        // Condition 5: regime must NOT be TRENDING_DOWN
        if ("TRENDING_DOWN".equals(regime)) {
            return Optional.empty();
        }

        for (int i = 1; i < todayCandles.size(); i++) {
            Candle prev = todayCandles.get(i - 1);
            Candle curr = todayCandles.get(i);

            // Skip synthetic candles
            if (curr.isSynthetic() || prev.isSynthetic()) continue;

            // Check detection window (10:00-14:30 IST)
            LocalTime currTime = curr.getCandleTime().atZone(IST).toLocalTime();
            if (currTime.isBefore(WINDOW_START) || currTime.isAfter(WINDOW_END)) continue;

            // RSI and VWAP must be present
            BigDecimal prevRsi = prev.getRsi();
            BigDecimal currRsi = curr.getRsi();
            BigDecimal currVwap = curr.getVwap();
            if (prevRsi == null || currRsi == null || currVwap == null) continue;

            // Condition 1: prev RSI < 25 (oversold)
            if (prevRsi.compareTo(RSI_THRESHOLD) >= 0) continue;

            // Condition 2: curr RSI > 25 AND rising (curr > prev)
            if (currRsi.compareTo(RSI_THRESHOLD) <= 0) continue;
            if (currRsi.compareTo(prevRsi) <= 0) continue;

            // Condition 3: close within 1% of VWAP
            BigDecimal currClose = curr.getClose();
            BigDecimal proxPct = currClose.subtract(currVwap).abs()
                    .divide(currVwap, 6, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            if (proxPct.compareTo(VWAP_PROX_PCT) > 0) continue;

            // Condition 4: volume >= 1.5× baseline
            Long baseline = volumeBaselines.get(currTime);
            if (!volumeOk(curr.getVolume(), baseline)) continue;

            // All conditions met — build signal (includes R:R check)
            Optional<Signal> signal = buildSignal(stock, prev, curr, currVwap, regime);
            if (signal.isPresent()) return signal;
        }
        return Optional.empty();
    }

    private Optional<Signal> buildSignal(Stock stock, Candle prev, Candle curr,
                                          BigDecimal vwap, String regime) {
        BigDecimal entry   = curr.getClose().setScale(2, RoundingMode.HALF_UP);
        BigDecimal stopLoss = prev.getLow().setScale(2, RoundingMode.HALF_UP);
        BigDecimal risk    = entry.subtract(stopLoss);

        // Risk must be positive (entry above SL) and R:R >= 1.5
        if (risk.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        BigDecimal t1     = vwap.setScale(2, RoundingMode.HALF_UP);
        BigDecimal reward = t1.subtract(entry);
        if (reward.divide(risk, 4, RoundingMode.HALF_UP).compareTo(MIN_RR) < 0) {
            return Optional.empty();
        }

        BigDecimal t2 = entry.add(risk.multiply(TWO)).setScale(2, RoundingMode.HALF_UP);

        int regimeModifier = regime != null ? parseModifier(regime) : 0;
        int confidence = Math.max(0, Math.min(100, 50 + regimeModifier));

        Signal signal = new Signal(
                stock, SignalType.RSI_OVERSOLD_BOUNCE, SignalDirection.LONG,
                entry, t1, t2, stopLoss,
                confidence, regime != null ? regime : "SIDEWAYS",
                curr.getCandleTime(),
                curr.getCandleTime().plus(30, ChronoUnit.MINUTES),
                null, null
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

    @Override public String detectorName() { return "RsiOversoldBounceDetector"; }
    @Override public SignalType signalType() { return SignalType.RSI_OVERSOLD_BOUNCE; }
    @Override public int maxSignalsPerDay() { return MAX_SIGNALS_PER_DAY; }
}
