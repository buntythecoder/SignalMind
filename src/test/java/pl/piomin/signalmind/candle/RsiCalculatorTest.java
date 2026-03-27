package pl.piomin.signalmind.candle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.candle.service.RsiCalculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RsiCalculator}.
 *
 * <p>All tests use only Java standard library and JUnit 5 — no Spring context
 * or database required.
 *
 * <p>SM-19
 */
class RsiCalculatorTest {

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RSI with 14 or fewer closes returns null (insufficient data)")
    void insufficientData_returnsNull() {
        // Exactly PERIOD entries — need at least PERIOD+1 to compute one change
        List<BigDecimal> closes = buildLinear(100.0, 1.0, RsiCalculator.PERIOD);
        assertNull(RsiCalculator.compute(closes),
                "Should return null when list has exactly PERIOD entries");

        assertNull(RsiCalculator.compute(null), "Null input should return null");

        assertNull(RsiCalculator.compute(List.of()),
                "Empty list should return null");

        assertNull(RsiCalculator.compute(List.of(BigDecimal.TEN)),
                "Single-element list should return null");
    }

    // ── Boundary values ───────────────────────────────────────────────────────

    @Test
    @DisplayName("All-gain series (every close rises by 1.0) gives RSI = 100.00")
    void allGainsGive100Rsi() {
        // 15 prices each increasing by 1.0 → 14 consecutive gains, 0 losses
        List<BigDecimal> closes = buildLinear(100.0, +1.0, 15);
        BigDecimal rsi = RsiCalculator.compute(closes);

        assertNotNull(rsi, "RSI must not be null for 15 closes");
        assertEquals(new BigDecimal("100.00"), rsi,
                "All-gain series should produce RSI = 100.00");
    }

    @Test
    @DisplayName("All-loss series (every close falls by 1.0) gives RSI = 0.00")
    void allLossesGive0Rsi() {
        // 15 prices each decreasing by 1.0 → 0 gains, 14 consecutive losses
        List<BigDecimal> closes = buildLinear(200.0, -1.0, 15);
        BigDecimal rsi = RsiCalculator.compute(closes);

        assertNotNull(rsi, "RSI must not be null for 15 closes");
        assertEquals(new BigDecimal("0.00"), rsi,
                "All-loss series should produce RSI = 0.00");
    }

    // ── Known-value verification ──────────────────────────────────────────────

    /**
     * Wilder's original RSI example from "New Concepts in Technical Trading Systems"
     * uses 14 periods. The closes below are a simplified synthetic series that
     * produces a well-known mid-range RSI. We assert within ±0.5 to tolerate
     * small rounding differences in different implementations.
     *
     * <p>Series: 14 closes alternating +1 / −0.5 (net upward bias).
     * Expected RSI is in the range [60, 75] for this pattern with 15 data points.
     */
    @Test
    @DisplayName("Alternating gain/loss series produces mid-range RSI (within ±0.5 of reference)")
    void knownRsiValue() {
        // 15 closes: 100, 101, 100.5, 101.5, 101.0, 102.0, 101.5, 102.5,
        //            102.0, 103.0, 102.5, 103.5, 103.0, 104.0, 103.5
        // Pattern: +1, -0.5 alternating → avg gain > avg loss, RSI should be > 50
        List<BigDecimal> closes = new ArrayList<>();
        double price = 100.0;
        closes.add(BigDecimal.valueOf(price));
        for (int i = 0; i < 14; i++) {
            price += (i % 2 == 0) ? 1.0 : -0.5;
            closes.add(BigDecimal.valueOf(price));
        }

        BigDecimal rsi = RsiCalculator.compute(closes);
        assertNotNull(rsi, "RSI must not be null for 15 closes");

        // With alternating +1/-0.5 over 14 changes:
        //   gains sum = 7 × 1.0 = 7.0, avgGain = 0.5
        //   losses sum = 7 × 0.5 = 3.5, avgLoss = 0.25
        //   RS = 2.0, RSI = 100 - 100/(1+2) = 66.67
        BigDecimal expected = new BigDecimal("66.67");
        BigDecimal tolerance = new BigDecimal("0.5");
        assertTrue(rsi.subtract(expected).abs().compareTo(tolerance) <= 0,
                "RSI " + rsi + " should be within 0.5 of " + expected);
    }

    /**
     * Wilder-smoothed RSI with more data points (30 closes, alternating pattern).
     * As more data is fed through Wilder smoothing the RSI converges and should
     * remain in a stable mid-range band. We verify it stays between 50 and 90.
     */
    @Test
    @DisplayName("Wilder smoothing with 30 closes converges to stable mid-range RSI")
    void wilderSmoothingConverges() {
        List<BigDecimal> closes = new ArrayList<>();
        double price = 100.0;
        closes.add(BigDecimal.valueOf(price));
        for (int i = 0; i < 29; i++) {
            price += (i % 2 == 0) ? 1.0 : -0.5;
            closes.add(BigDecimal.valueOf(price));
        }

        BigDecimal rsi = RsiCalculator.compute(closes);
        assertNotNull(rsi);
        assertTrue(rsi.compareTo(new BigDecimal("50")) > 0,
                "RSI should be above 50 for net-positive series");
        assertTrue(rsi.compareTo(new BigDecimal("90")) < 0,
                "RSI should be below 90 — series is not pure gains");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Build a monotonically increasing or decreasing price series.
     *
     * @param start initial price
     * @param step  per-period change (positive = gains, negative = losses)
     * @param count total number of prices (not changes)
     */
    private List<BigDecimal> buildLinear(double start, double step, int count) {
        List<BigDecimal> closes = new ArrayList<>();
        double price = start;
        for (int i = 0; i < count; i++) {
            closes.add(BigDecimal.valueOf(price));
            price += step;
        }
        return closes;
    }
}
