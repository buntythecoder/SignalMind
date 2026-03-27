package pl.piomin.signalmind.regime;

import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.regime.indicator.EmaCalculator;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EmaCalculatorTest {

    @Test
    void compute_returnsNull_whenInsufficientData() {
        List<BigDecimal> prices = List.of(bd("100"), bd("101"), bd("102"));

        BigDecimal result = EmaCalculator.compute(prices, 5);

        assertThat(result).isNull();
    }

    @Test
    void compute_returnsNull_whenPricesIsNull() {
        assertThat(EmaCalculator.compute(null, 3)).isNull();
    }

    @Test
    void compute_singlePeriod_returnsThatPrice() {
        List<BigDecimal> prices = List.of(bd("250.50"));

        BigDecimal result = EmaCalculator.compute(prices, 1);

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(bd("250.50"));
    }

    @Test
    void compute_exactlyPeriodValues_returnsSma() {
        // With exactly `period` values, EMA seed = SMA and no further iteration
        // SMA of [10, 20, 30] = 20.0
        List<BigDecimal> prices = List.of(bd("10"), bd("20"), bd("30"));

        BigDecimal result = EmaCalculator.compute(prices, 3);

        assertThat(result).isNotNull();
        assertThat(result.doubleValue()).isCloseTo(20.0, within(0.01));
    }

    @Test
    void compute_knownSequence_ema3() {
        // 6-value series: [10, 11, 12, 13, 14, 15]
        // Seed (SMA of first 3): (10+11+12)/3 = 11.0
        // k = 2/(3+1) = 0.5
        // EMA after bar 4 (value=13): 13*0.5 + 11.0*0.5 = 12.0
        // EMA after bar 5 (value=14): 14*0.5 + 12.0*0.5 = 13.0
        // EMA after bar 6 (value=15): 15*0.5 + 13.0*0.5 = 14.0
        List<BigDecimal> prices = List.of(
                bd("10"), bd("11"), bd("12"), bd("13"), bd("14"), bd("15"));

        BigDecimal result = EmaCalculator.compute(prices, 3);

        assertThat(result).isNotNull();
        assertThat(result.doubleValue()).isCloseTo(14.0, within(0.01));
    }

    @Test
    void compute_period20_returnsNonNull_whenEnoughData() {
        // 25 ascending values — enough for EMA(20)
        List<BigDecimal> prices = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            prices.add(BigDecimal.valueOf(100 + i));
        }

        BigDecimal result = EmaCalculator.compute(prices, 20);

        assertThat(result).isNotNull();
        assertThat(result.doubleValue()).isGreaterThan(100.0);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
