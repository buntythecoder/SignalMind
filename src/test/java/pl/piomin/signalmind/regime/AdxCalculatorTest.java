package pl.piomin.signalmind.regime;

import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.regime.indicator.AdxCalculator;
import pl.piomin.signalmind.regime.indicator.OhlcBar;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdxCalculatorTest {

    private static final int PERIOD = 14;
    /** Minimum bars required: 2 * PERIOD + 1 = 29 */
    private static final int MIN_BARS = 2 * PERIOD + 1;

    @Test
    void compute_returnsNull_whenBarsIsNull() {
        assertThat(AdxCalculator.compute(null, PERIOD)).isNull();
    }

    @Test
    void compute_returnsNull_whenInsufficientData() {
        // 28 bars is one less than the minimum of 29
        List<OhlcBar> bars = trendingUpBars(28);

        assertThat(AdxCalculator.compute(bars, PERIOD)).isNull();
    }

    @Test
    void compute_exactlyMinimumBars_returnsNonNull() {
        List<OhlcBar> bars = trendingUpBars(MIN_BARS);

        BigDecimal dx = AdxCalculator.compute(bars, PERIOD);

        assertThat(dx).isNotNull();
    }

    @Test
    void compute_trendingMarket_highDx() {
        // Strongly trending bars: each bar's high and low consistently step up
        // → large +DM, tiny -DM → high DX
        List<OhlcBar> bars = trendingUpBars(MIN_BARS + 5);

        BigDecimal dx = AdxCalculator.compute(bars, PERIOD);

        assertThat(dx).isNotNull();
        assertThat(dx.doubleValue())
                .as("Strongly trending market should produce DX >= 25")
                .isGreaterThanOrEqualTo(25.0);
    }

    @Test
    void compute_sidewaysMarket_lowDx() {
        // Perfectly flat bars: every bar has the same H/L/O/C.
        // Up-move = curr.high - prev.high = 0; down-move = prev.low - curr.low = 0.
        // → +DM = 0, -DM = 0 for every bar → smoothed DM both = 0 → DX = 0.
        List<OhlcBar> bars = flatBars(MIN_BARS + 5);

        BigDecimal dx = AdxCalculator.compute(bars, PERIOD);

        assertThat(dx).isNotNull();
        assertThat(dx.doubleValue())
                .as("Flat market with no directional movement should produce DX = 0")
                .isEqualTo(0.0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a strongly trending-up bar series.
     * Each bar is 1 point higher than the previous; High is always up, Low is always up.
     */
    private List<OhlcBar> trendingUpBars(int count) {
        List<OhlcBar> bars = new ArrayList<>();
        double base = 100.0;
        for (int i = 0; i < count; i++) {
            double step = i * 2.0;
            bars.add(bar(base + step, base + step + 3, base + step + 0.5, base + step + 2.5));
        }
        return bars;
    }

    /**
     * Builds a perfectly flat bar series where every bar is identical.
     * Up-move = curr.high - prev.high = 0 and down-move = 0 for every bar,
     * so both +DM and -DM remain zero throughout, producing DX = 0.
     */
    private List<OhlcBar> flatBars(int count) {
        List<OhlcBar> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bars.add(bar(100, 102, 98, 100)); // identical OHLC every bar
        }
        return bars;
    }

    private static OhlcBar bar(double open, double high, double low, double close) {
        return new OhlcBar(
                BigDecimal.valueOf(open),
                BigDecimal.valueOf(high),
                BigDecimal.valueOf(low),
                BigDecimal.valueOf(close)
        );
    }
}
