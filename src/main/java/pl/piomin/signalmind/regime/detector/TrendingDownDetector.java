package pl.piomin.signalmind.regime.detector;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.regime.indicator.AdxCalculator;
import pl.piomin.signalmind.regime.indicator.EmaCalculator;
import pl.piomin.signalmind.regime.indicator.OhlcBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Detects {@link MarketRegime#TRENDING_DOWN} (SM-21, priority 3).
 *
 * <p>All three conditions must hold:
 * <ol>
 *   <li>The most recent closing price is below EMA(20),</li>
 *   <li>ADX(14) &ge; 25 (strong directional movement), and</li>
 *   <li>The last 5 bars show strictly lower highs.</li>
 * </ol>
 */
@Component
public class TrendingDownDetector implements RegimeDetector {

    private static final int        EMA_PERIOD      = 20;
    private static final int        ADX_PERIOD      = 14;
    private static final BigDecimal ADX_THRESHOLD   = BigDecimal.valueOf(25);
    private static final int        LOWER_HIGH_BARS = 5;

    @Override
    public Optional<MarketRegime> detect(List<OhlcBar> bars,
                                         BigDecimal currentVix,
                                         BigDecimal vwap,
                                         Instant latestBarTime) {
        if (bars == null || bars.size() < EMA_PERIOD) {
            return Optional.empty();
        }

        // Condition 1: last close < EMA(20)
        List<BigDecimal> closes = bars.stream()
                .map(OhlcBar::close)
                .collect(Collectors.toList());
        BigDecimal ema = EmaCalculator.compute(closes, EMA_PERIOD);
        if (ema == null) {
            return Optional.empty();
        }
        BigDecimal lastClose = closes.get(closes.size() - 1);
        if (lastClose.compareTo(ema) >= 0) {
            return Optional.empty();
        }

        // Condition 2: ADX(14) >= 25
        BigDecimal adx = AdxCalculator.compute(bars, ADX_PERIOD);
        if (adx == null || adx.compareTo(ADX_THRESHOLD) < 0) {
            return Optional.empty();
        }

        // Condition 3: last 5 bars show strictly lower highs
        if (!hasLowerHighs(bars)) {
            return Optional.empty();
        }

        return Optional.of(MarketRegime.TRENDING_DOWN);
    }

    /**
     * Returns true when the last {@link #LOWER_HIGH_BARS} bars have strictly
     * descending highs (bars[-1].high &lt; bars[-2].high &lt; … &lt; bars[-5].high).
     */
    private boolean hasLowerHighs(List<OhlcBar> bars) {
        if (bars.size() < LOWER_HIGH_BARS) {
            return false;
        }
        int tail = bars.size();
        for (int i = tail - 1; i >= tail - LOWER_HIGH_BARS + 1; i--) {
            if (bars.get(i).high().compareTo(bars.get(i - 1).high()) >= 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public String detectorName() {
        return "TrendingDownDetector";
    }
}
