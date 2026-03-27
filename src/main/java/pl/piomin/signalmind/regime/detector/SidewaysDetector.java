package pl.piomin.signalmind.regime.detector;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.regime.indicator.AdxCalculator;
import pl.piomin.signalmind.regime.indicator.OhlcBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Detects {@link MarketRegime#SIDEWAYS} — the default fallback (SM-21, priority 4).
 *
 * <p>Triggers when:
 * <ul>
 *   <li>ADX(14) &lt; 20 (no strong directional movement), <em>or</em></li>
 *   <li>The last 15 closing prices are all within ±0.5% of the supplied VWAP
 *       (price consolidation around VWAP).</li>
 * </ul>
 * When VWAP is {@code null} or zero, only the ADX condition is evaluated.
 */
@Component
public class SidewaysDetector implements RegimeDetector {

    private static final int        ADX_PERIOD       = 14;
    private static final BigDecimal ADX_SIDEWAYS_MAX = BigDecimal.valueOf(20);
    private static final int        VWAP_BARS        = 15;
    private static final BigDecimal VWAP_BAND        = new BigDecimal("0.005"); // 0.5%

    @Override
    public Optional<MarketRegime> detect(List<OhlcBar> bars,
                                         BigDecimal currentVix,
                                         BigDecimal vwap,
                                         Instant latestBarTime) {
        if (bars == null || bars.isEmpty()) {
            return Optional.empty();
        }

        // Condition 1: ADX < 20
        BigDecimal adx = AdxCalculator.compute(bars, ADX_PERIOD);
        if (adx != null && adx.compareTo(ADX_SIDEWAYS_MAX) < 0) {
            return Optional.of(MarketRegime.SIDEWAYS);
        }

        // Condition 2: last 15 closes within 0.5% of VWAP
        if (vwap != null && vwap.compareTo(BigDecimal.ZERO) != 0
                && bars.size() >= VWAP_BARS) {
            if (allWithinVwapBand(bars, vwap)) {
                return Optional.of(MarketRegime.SIDEWAYS);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns true when all of the last {@link #VWAP_BARS} closing prices fall
     * within VWAP ± 0.5%.
     */
    private boolean allWithinVwapBand(List<OhlcBar> bars, BigDecimal vwap) {
        BigDecimal upper = vwap.multiply(BigDecimal.ONE.add(VWAP_BAND))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal lower = vwap.multiply(BigDecimal.ONE.subtract(VWAP_BAND))
                .setScale(4, RoundingMode.HALF_UP);

        int size = bars.size();
        for (int i = size - VWAP_BARS; i < size; i++) {
            BigDecimal close = bars.get(i).close();
            if (close.compareTo(lower) < 0 || close.compareTo(upper) > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public String detectorName() {
        return "SidewaysDetector";
    }
}
