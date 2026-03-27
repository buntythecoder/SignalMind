package pl.piomin.signalmind.regime;

import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.regime.indicator.AtrCalculator;
import pl.piomin.signalmind.regime.indicator.OhlcBar;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AtrCalculatorTest {

    @Test
    void compute_returnsNull_whenBarsIsNull() {
        assertThat(AtrCalculator.compute(null, 3)).isNull();
    }

    @Test
    void compute_returnsNull_whenInsufficientData() {
        // period=3 requires at least period+1=4 bars
        List<OhlcBar> bars = List.of(
                bar(10, 12, 9, 11),
                bar(11, 13, 10, 12),
                bar(12, 14, 11, 13)
        );

        assertThat(AtrCalculator.compute(bars, 3)).isNull();
    }

    @Test
    void compute_knownValues_atr3() {
        // 5-bar series with ATR period = 3
        // Bar 0: O=10 H=12 L=9  C=11  (anchor — prev close only)
        // Bar 1: O=11 H=13 L=10 C=12  TR = max(3, |13-11|, |10-11|) = max(3,2,1) = 3
        // Bar 2: O=12 H=14 L=11 C=13  TR = max(3, |14-12|, |11-12|) = max(3,2,1) = 3
        // Bar 3: O=13 H=15 L=12 C=14  TR = max(3, |15-13|, |12-13|) = max(3,2,1) = 3
        // Seed ATR (bars 1..3): (3+3+3)/3 = 3.0
        // Bar 4: O=14 H=16 L=13 C=15  TR = max(3, |16-14|, |13-14|) = max(3,2,1) = 3
        // Wilder: ATR = (3.0 * 2 + 3.0) / 3 = 3.0
        List<OhlcBar> bars = List.of(
                bar(10, 12, 9,  11),
                bar(11, 13, 10, 12),
                bar(12, 14, 11, 13),
                bar(13, 15, 12, 14),
                bar(14, 16, 13, 15)
        );

        BigDecimal atr = AtrCalculator.compute(bars, 3);

        assertThat(atr).isNotNull();
        assertThat(atr.doubleValue()).isCloseTo(3.0, within(0.01));
    }

    @Test
    void compute_exactlyPeriodPlusOne_returnsSeedAtr() {
        // Exactly period+1=4 bars with period=3 — seed only, no Wilder iterations.
        // For each bar the True Range = max(H-L, |H-prevC|, |L-prevC|).
        // Bars all have H=102, L=98 → HL = 4. prevClose = close of previous bar = 100.
        // |H-prevC| = |102-100| = 2, |L-prevC| = |98-100| = 2. TR = max(4,2,2) = 4.
        // Seed ATR = (4+4+4)/3 = 4.0
        List<OhlcBar> bars = List.of(
                bar(100, 102, 98, 100),
                bar(100, 102, 98, 100),
                bar(100, 102, 98, 100),
                bar(100, 102, 98, 100)
        );

        BigDecimal atr = AtrCalculator.compute(bars, 3);

        assertThat(atr).isNotNull();
        assertThat(atr.doubleValue()).isCloseTo(4.0, within(0.01));
    }

    @Test
    void trueRange_highGap_usesHPcComponent() {
        // High far above prevClose — |High - PrevClose| dominates
        OhlcBar bar = bar(200, 220, 199, 210);
        BigDecimal prevClose = bd("200");

        BigDecimal tr = AtrCalculator.trueRange(bar, prevClose);

        // HL = 21, |220-200| = 20, |199-200| = 1 → TR = 21
        assertThat(tr.doubleValue()).isCloseTo(21.0, within(0.01));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static OhlcBar bar(double open, double high, double low, double close) {
        return new OhlcBar(bd(open), bd(high), bd(low), bd(close));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
