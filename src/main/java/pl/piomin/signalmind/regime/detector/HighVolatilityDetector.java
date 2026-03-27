package pl.piomin.signalmind.regime.detector;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.regime.indicator.AtrCalculator;
import pl.piomin.signalmind.regime.indicator.OhlcBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Detects {@link MarketRegime#HIGH_VOLATILITY} (SM-21, priority 1).
 *
 * <p>Triggers when:
 * <ul>
 *   <li>India VIX is available AND VIX &gt; 20, <em>or</em></li>
 *   <li>VIX data is stale ({@code currentVix == null}) AND the ATR of the most
 *       recent 14 bars exceeds 1.5× the baseline ATR of the oldest 14 bars
 *       in the supplied series.</li>
 * </ul>
 */
@Component
public class HighVolatilityDetector implements RegimeDetector {

    private static final BigDecimal VIX_THRESHOLD     = BigDecimal.valueOf(20);
    private static final BigDecimal ATR_MULTIPLIER     = BigDecimal.valueOf(1.5);
    private static final int        ATR_PERIOD         = 14;

    @Override
    public Optional<MarketRegime> detect(List<OhlcBar> bars,
                                         BigDecimal currentVix,
                                         BigDecimal vwap,
                                         Instant latestBarTime) {
        // Path 1: VIX is fresh — compare directly
        if (currentVix != null) {
            if (currentVix.compareTo(VIX_THRESHOLD) > 0) {
                return Optional.of(MarketRegime.HIGH_VOLATILITY);
            }
            // VIX is available but not elevated — not high-volatility
            return Optional.empty();
        }

        // Path 2: VIX is stale → ATR-only fallback
        if (bars == null || bars.size() < 2 * ATR_PERIOD + 1) {
            return Optional.empty(); // insufficient data for fallback
        }

        // Baseline ATR: oldest ATR_PERIOD+1 bars
        List<OhlcBar> baselineBars = bars.subList(0, ATR_PERIOD + 1);
        BigDecimal baselineAtr = AtrCalculator.compute(baselineBars, ATR_PERIOD);

        // Recent ATR: last ATR_PERIOD+1 bars
        int size = bars.size();
        List<OhlcBar> recentBars = bars.subList(size - ATR_PERIOD - 1, size);
        BigDecimal recentAtr = AtrCalculator.compute(recentBars, ATR_PERIOD);

        if (baselineAtr == null || recentAtr == null
                || baselineAtr.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        BigDecimal threshold = baselineAtr.multiply(ATR_MULTIPLIER)
                .setScale(4, RoundingMode.HALF_UP);

        if (recentAtr.compareTo(threshold) > 0) {
            return Optional.of(MarketRegime.HIGH_VOLATILITY);
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public String detectorName() {
        return "HighVolatilityDetector";
    }
}
