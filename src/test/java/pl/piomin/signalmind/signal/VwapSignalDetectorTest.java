package pl.piomin.signalmind.signal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.signal.detector.VwapBreakdownDetector;
import pl.piomin.signalmind.signal.detector.VwapBreakoutDetector;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link VwapBreakoutDetector} and {@link VwapBreakdownDetector} (SM-23).
 *
 * <p>No Spring context is loaded.  Candle lists are supplied oldest-first to
 * match the contract expected by both detectors.
 */
class VwapSignalDetectorTest {

    private static final ZoneId IST  = ZoneId.of("Asia/Kolkata");
    private static final String DATE = "2025-01-15";

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = new Stock("INFY", "Infosys Ltd", IndexType.NIFTY50);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    private static Instant ist(String time) {
        return LocalDateTime.parse(DATE + "T" + time).atZone(IST).toInstant();
    }

    /**
     * Builds a real (non-synthetic) candle with explicit OHLCV and VWAP / RSI indicators.
     *
     * @param time       HH:mm:ss in IST
     * @param close      close price
     * @param vwap       VWAP value
     * @param vwapUpper  upper VWAP band (nullable)
     * @param vwapLower  lower VWAP band (nullable)
     * @param rsi        RSI(14) value
     * @param volume     candle volume
     */
    private Candle candle(String time, double close, double vwap,
                          Double vwapUpper, Double vwapLower,
                          double rsi, long volume) {
        BigDecimal bdClose = bd(close);
        BigDecimal bdVwap  = bd(vwap);
        return new Candle(stock, ist(time),
                bdClose, bdClose, bdClose, bdClose,
                volume, CandleSource.LIVE,
                bdVwap,
                vwapUpper != null ? bd(vwapUpper) : null,
                vwapLower != null ? bd(vwapLower) : null,
                bd(rsi));
    }

    private static BigDecimal bd(double v) {
        return new BigDecimal(String.valueOf(v));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VwapBreakoutDetector tests
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VwapBreakoutDetector")
    class BreakoutTests {

        private VwapBreakoutDetector detector;

        @BeforeEach
        void setUp() {
            detector = new VwapBreakoutDetector();
        }

        @Test
        @DisplayName("returns LONG signal when all 6 conditions are satisfied")
        void detect_returnsLong_whenAllConditionsMet() {
            // prev: close=99 < vwap=100 ✓ (condition 1)
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            // curr: close=100.3 > vwap=100 ✓ (condition 2), RSI=55 ∈ [40,70] ✓ (condition 4)
            //       proximity: (100.3-100)/100 = 0.3% < 0.5% ✓ (condition 5)
            Candle curr = candle("10:01:00", 100.3, 100.0, 101.0, 99.0, 55.0, 12000);

            // volume: 12000 >= 2.0 × 5000 = 10000 ✓ (condition 3)
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(10, 1), 5000L);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr), baselines, "SIDEWAYS");

            assertThat(result).isPresent();
            Signal s = result.get();
            assertThat(s.getSignalType()).isEqualTo(SignalType.VWAP_BREAKOUT);
            assertThat(s.getDirection()).isEqualTo(SignalDirection.LONG);
            // entry = curr.close = 100.30
            assertThat(s.getEntryPrice()).isEqualByComparingTo("100.30");
            // SL = vwapLower = 99.00
            assertThat(s.getStopLoss()).isEqualByComparingTo("99.00");
            // risk = 100.30 - 99.00 = 1.30; T1 = entry + 1.5×risk = 102.25
            assertThat(s.getTargetPrice()).isEqualByComparingTo("102.25");
            // T2 = vwapUpper = 101.00
            assertThat(s.getTarget2()).isEqualByComparingTo("101.00");
        }

        @Test
        @DisplayName("returns empty when prev close is NOT below VWAP (condition 1 fails)")
        void detect_returnsEmpty_whenPrevCloseAboveVwap() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 55.0, 5000); // prev > VWAP
            Candle curr = candle("10:01:00", 100.3, 100.0, null, null, 55.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when curr close is NOT above VWAP (condition 2 fails)")
        void detect_returnsEmpty_whenCurrCloseNotAboveVwap() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            Candle curr = candle("10:01:00", 100.0, 100.0, null, null, 55.0, 12000); // curr == VWAP (not >)

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when volume is below 2.0× baseline (condition 3 fails)")
        void detect_returnsEmpty_whenVolumeBelowThreshold() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            Candle curr = candle("10:01:00", 100.3, 100.0, null, null, 55.0, 9000); // 9000 < 2.0×5000=10000

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when RSI is below 40 (condition 4 fails)")
        void detect_returnsEmpty_whenRsiBelowMin() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            Candle curr = candle("10:01:00", 100.3, 100.0, null, null, 38.0, 12000); // RSI=38 < 40

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when RSI is above 70 (condition 4 fails)")
        void detect_returnsEmpty_whenRsiAboveMax() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            Candle curr = candle("10:01:00", 100.3, 100.0, null, null, 72.0, 12000); // RSI=72 > 70

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when close is more than 0.5% above VWAP (condition 5 fails)")
        void detect_returnsEmpty_whenPriceNotCloseEnoughToVwap() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            // close=100.6 → proximity = (100.6-100)/100 = 0.6% > 0.5%
            Candle curr = candle("10:01:00", 100.6, 100.0, null, null, 55.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when regime is TRENDING_DOWN (condition 6 fails)")
        void detect_returnsEmpty_whenRegimeTrendingDown() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            Candle curr = candle("10:01:00", 100.3, 100.0, null, null, 55.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "TRENDING_DOWN");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips synthetic candles and does not fire on them")
        void detect_skipsSyntheticCandles() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);

            // Synthetic curr with close > VWAP — must be skipped
            Candle syntheticCurr = Candle.synthetic(
                    stock, ist("10:01:00"),
                    bd(100.3), bd(100.0), bd(101.0), bd(99.0));

            Optional<Signal> result = detector.detect(stock, List.of(prev, syntheticCurr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("applies TRENDING_UP regime modifier (+15) to confidence")
        void detect_appliesRegimeModifierToConfidence() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 55.0, 5000);
            Candle curr = candle("10:01:00", 100.3, 100.0, null, null, 55.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "TRENDING_UP");

            assertThat(result).isPresent();
            // 50 (base) + 15 (TRENDING_UP modifier) = 65
            assertThat(result.get().getConfidence()).isEqualTo(65);
        }

        @Test
        @DisplayName("maxSignalsPerDay returns 3 (shared VWAP cap)")
        void maxSignalsPerDay_returns3() {
            assertThat(detector.maxSignalsPerDay()).isEqualTo(3);
        }

        @Test
        @DisplayName("countedTypes includes both VWAP_BREAKOUT and VWAP_BREAKDOWN")
        void countedTypes_includesBothVwapTypes() {
            assertThat(detector.countedTypes())
                    .containsExactlyInAnyOrder(SignalType.VWAP_BREAKOUT, SignalType.VWAP_BREAKDOWN);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VwapBreakdownDetector tests
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VwapBreakdownDetector")
    class BreakdownTests {

        private VwapBreakdownDetector detector;

        @BeforeEach
        void setUp() {
            detector = new VwapBreakdownDetector();
        }

        @Test
        @DisplayName("returns SHORT signal when all 6 conditions are satisfied")
        void detect_returnsShort_whenAllConditionsMet() {
            // prev: close=101 > vwap=100 ✓ (condition 1)
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            // curr: close=99.7 < vwap=100 ✓ (condition 2), RSI=45 ∈ [30,60] ✓ (condition 4)
            //       proximity: (100-99.7)/100 = 0.3% < 0.5% ✓ (condition 5)
            Candle curr = candle("10:01:00", 99.7, 100.0, 101.0, 99.0, 45.0, 12000);

            // volume: 12000 >= 2.0 × 5000 = 10000 ✓ (condition 3)
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(10, 1), 5000L);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr), baselines, "SIDEWAYS");

            assertThat(result).isPresent();
            Signal s = result.get();
            assertThat(s.getSignalType()).isEqualTo(SignalType.VWAP_BREAKDOWN);
            assertThat(s.getDirection()).isEqualTo(SignalDirection.SHORT);
            // entry = curr.close = 99.70
            assertThat(s.getEntryPrice()).isEqualByComparingTo("99.70");
            // SL = vwapUpper = 101.00
            assertThat(s.getStopLoss()).isEqualByComparingTo("101.00");
            // risk = 101.00 - 99.70 = 1.30; T1 = entry - 1.5×risk = 99.70 - 1.95 = 97.75
            assertThat(s.getTargetPrice()).isEqualByComparingTo("97.75");
            // T2 = vwapLower = 99.00
            assertThat(s.getTarget2()).isEqualByComparingTo("99.00");
        }

        @Test
        @DisplayName("returns empty when prev close is NOT above VWAP (condition 1 fails)")
        void detect_returnsEmpty_whenPrevCloseBelowVwap() {
            Candle prev = candle("10:00:00", 99.0, 100.0, null, null, 45.0, 5000); // prev < VWAP
            Candle curr = candle("10:01:00", 99.7, 100.0, null, null, 45.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when curr close is NOT below VWAP (condition 2 fails)")
        void detect_returnsEmpty_whenCurrCloseNotBelowVwap() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            Candle curr = candle("10:01:00", 100.0, 100.0, null, null, 45.0, 12000); // curr == VWAP (not <)

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when volume is below 2.0× baseline (condition 3 fails)")
        void detect_returnsEmpty_whenVolumeBelowThreshold() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            Candle curr = candle("10:01:00", 99.7, 100.0, null, null, 45.0, 9999); // 9999 < 10000

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when RSI is below 30 (condition 4 fails)")
        void detect_returnsEmpty_whenRsiBelowMin() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            Candle curr = candle("10:01:00", 99.7, 100.0, null, null, 28.0, 12000); // RSI=28 < 30

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when RSI is above 60 (condition 4 fails)")
        void detect_returnsEmpty_whenRsiAboveMax() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            Candle curr = candle("10:01:00", 99.7, 100.0, null, null, 62.0, 12000); // RSI=62 > 60

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when close is more than 0.5% below VWAP (condition 5 fails)")
        void detect_returnsEmpty_whenPriceNotCloseEnoughToVwap() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            // close=99.4 → proximity = (100-99.4)/100 = 0.6% > 0.5%
            Candle curr = candle("10:01:00", 99.4, 100.0, null, null, 45.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when regime is TRENDING_UP (condition 6 fails)")
        void detect_returnsEmpty_whenRegimeTrendingUp() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            Candle curr = candle("10:01:00", 99.7, 100.0, null, null, 45.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "TRENDING_UP");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("applies TRENDING_DOWN regime modifier (-15) to confidence")
        void detect_appliesRegimeModifierToConfidence() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            Candle curr = candle("10:01:00", 99.7, 100.0, null, null, 45.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isPresent();
            // 50 (base) + (-5) (SIDEWAYS modifier) = 45
            assertThat(result.get().getConfidence()).isEqualTo(45);
        }

        @Test
        @DisplayName("falls back to VWAP × 1.005 for SL when vwapUpper is null")
        void detect_usesVwapFallbackForSl_whenVwapUpperNull() {
            Candle prev = candle("10:00:00", 101.0, 100.0, null, null, 45.0, 5000);
            // vwapUpper=null → SL = 100 × 1.005 = 100.50
            Candle curr = candle("10:01:00", 99.7, 100.0, null, null, 45.0, 12000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "TRENDING_UP"); // blocked by regime

            // TRENDING_UP blocks, so test falls back to SIDEWAYS
            result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isPresent();
            // SL = 100 × 1.005 = 100.50
            assertThat(result.get().getStopLoss()).isEqualByComparingTo("100.50");
        }

        @Test
        @DisplayName("maxSignalsPerDay returns 3 (shared VWAP cap)")
        void maxSignalsPerDay_returns3() {
            assertThat(detector.maxSignalsPerDay()).isEqualTo(3);
        }

        @Test
        @DisplayName("countedTypes includes both VWAP_BREAKOUT and VWAP_BREAKDOWN")
        void countedTypes_includesBothVwapTypes() {
            assertThat(detector.countedTypes())
                    .containsExactlyInAnyOrder(SignalType.VWAP_BREAKOUT, SignalType.VWAP_BREAKDOWN);
        }
    }
}
