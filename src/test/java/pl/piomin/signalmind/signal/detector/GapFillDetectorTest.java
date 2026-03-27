package pl.piomin.signalmind.signal.detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.signal.detector.GapFillLongDetector;
import pl.piomin.signalmind.signal.detector.GapFillShortDetector;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pure unit tests for {@link GapFillLongDetector} and {@link GapFillShortDetector} (SM-25).
 *
 * <p>Uses the package-private {@code detectWithPrevClose} method to bypass
 * the CandleRepository call, enabling pure unit testing without a real database.
 */
class GapFillDetectorTest {

    private static final ZoneId IST  = ZoneId.of("Asia/Kolkata");
    private static final String DATE = "2025-01-15";

    private Stock stock;
    private CandleRepository mockRepo;

    @BeforeEach
    void setUp() {
        stock = new Stock("TCS", "Tata Consultancy Services", IndexType.NIFTY50);
        mockRepo = mock(CandleRepository.class);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    private static Instant ist(String time) {
        return LocalDateTime.parse(DATE + "T" + time).atZone(IST).toInstant();
    }

    /**
     * Builds a real (non-synthetic) candle with explicit OHLCV.
     * VWAP and RSI are not needed for gap fill detection.
     */
    private Candle candle(String time, double open, double high, double low, double close, long vol) {
        return new Candle(stock, ist(time),
                bd(open), bd(high), bd(low), bd(close),
                vol, CandleSource.LIVE,
                null, null, null, null);
    }

    private static BigDecimal bd(double v) {
        return new BigDecimal(String.valueOf(v));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GapFillLongDetector — Gap Down scenario
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GapFillLongDetector")
    class LongTests {

        private GapFillLongDetector detector;

        @BeforeEach
        void setUp() {
            detector = new GapFillLongDetector(mockRepo);
        }

        @Test
        @DisplayName("returns LONG signal when gap-down is within [0.5%, 3.0%] and volume confirms")
        void detect_returnsLong_whenGapDownInRange_withSufficientVolume() {
            // prevClose=1000, todayOpen=990 → gapPct=(1000-990)/1000×100=1.0% ∈ [0.5,3.0] ✓
            // volume: 21000 >= 2.0 × 10000 = 20000 ✓
            Candle first = candle("09:15:00", 990.0, 992.0, 985.0, 991.0, 21000);
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 15), 10000L);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), baselines, "SIDEWAYS", bd(1000.0));

            assertThat(result).isPresent();
            Signal s = result.get();
            assertThat(s.getSignalType()).isEqualTo(SignalType.GAP_FILL_LONG);
            assertThat(s.getDirection()).isEqualTo(SignalDirection.LONG);
            // entry = first.close = 991.00
            assertThat(s.getEntryPrice()).isEqualByComparingTo("991.00");
            // SL = first.low = 985.00
            assertThat(s.getStopLoss()).isEqualByComparingTo("985.00");
            // T1 = prevClose = 1000.00 (gap fill)
            assertThat(s.getTargetPrice()).isEqualByComparingTo("1000.00");
            // T2 = T1 + risk = 1000 + (991-985) = 1000 + 6 = 1006.00
            assertThat(s.getTarget2()).isEqualByComparingTo("1006.00");
        }

        @Test
        @DisplayName("returns empty when gap-down is below 0.5% (condition 1 fails)")
        void detect_returnsEmpty_whenGapTooSmall() {
            // prevClose=1000, todayOpen=997 → gapPct=0.3% < 0.5%
            Candle first = candle("09:15:00", 997.0, 999.0, 994.0, 998.0, 21000);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when gap-down exceeds 3.0% (condition 1 fails)")
        void detect_returnsEmpty_whenGapTooLarge() {
            // prevClose=1000, todayOpen=960 → gapPct=4.0% > 3.0%
            Candle first = candle("09:15:00", 960.0, 962.0, 955.0, 961.0, 21000);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when volume is below 2.0× baseline (condition 2 fails)")
        void detect_returnsEmpty_whenVolumeInsufficient() {
            // gap=1.0% ✓ but volume=15000 < 20000
            Candle first = candle("09:15:00", 990.0, 992.0, 985.0, 991.0, 15000);
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 15), 10000L);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), baselines, "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when candle is after 10:30 IST (outside window)")
        void detect_returnsEmpty_whenCandleAfterWindow() {
            Candle first = candle("10:31:00", 990.0, 992.0, 985.0, 991.0, 21000);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips synthetic candles")
        void detect_skipsSyntheticCandles() {
            Candle synthetic = Candle.synthetic(stock, ist("09:15:00"),
                    bd(990.0), bd(1005.0), bd(1010.0), bd(980.0));

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(synthetic), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when there is no gap (todayOpen == prevClose)")
        void detect_returnsEmpty_whenNoGap() {
            // prevClose=1000, todayOpen=1000 → gapPct=0% < 0.5%
            Candle first = candle("09:15:00", 1000.0, 1002.0, 998.0, 1001.0, 21000);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("valid_until is 15 minutes after generation")
        void detect_validUntil_is15MinutesAfterGeneration() {
            Candle first = candle("09:15:00", 990.0, 992.0, 985.0, 991.0, 21000);
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 15), 10000L);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), baselines, "SIDEWAYS", bd(1000.0));

            assertThat(result).isPresent();
            assertThat(result.get().getValidUntil())
                    .isEqualTo(ist("09:30:00"));
        }

        @Test
        @DisplayName("maxSignalsPerDay returns 1")
        void maxSignalsPerDay_returns1() {
            assertThat(detector.maxSignalsPerDay()).isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GapFillShortDetector — Gap Up scenario
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GapFillShortDetector")
    class ShortTests {

        private GapFillShortDetector detector;

        @BeforeEach
        void setUp() {
            detector = new GapFillShortDetector(mockRepo);
        }

        @Test
        @DisplayName("returns SHORT signal when gap-up is within [0.5%, 3.0%] and volume confirms")
        void detect_returnsShort_whenGapUpInRange_withSufficientVolume() {
            // prevClose=1000, todayOpen=1010 → gapPct=(1010-1000)/1000×100=1.0% ∈ [0.5,3.0] ✓
            // volume: 21000 >= 2.0 × 10000 = 20000 ✓
            Candle first = candle("09:15:00", 1010.0, 1015.0, 1008.0, 1011.0, 21000);
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 15), 10000L);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), baselines, "SIDEWAYS", bd(1000.0));

            assertThat(result).isPresent();
            Signal s = result.get();
            assertThat(s.getSignalType()).isEqualTo(SignalType.GAP_FILL_SHORT);
            assertThat(s.getDirection()).isEqualTo(SignalDirection.SHORT);
            // entry = first.close = 1011.00
            assertThat(s.getEntryPrice()).isEqualByComparingTo("1011.00");
            // SL = first.high = 1015.00
            assertThat(s.getStopLoss()).isEqualByComparingTo("1015.00");
            // T1 = prevClose = 1000.00 (gap fill)
            assertThat(s.getTargetPrice()).isEqualByComparingTo("1000.00");
            // T2 = T1 - risk = 1000 - (1015-1011) = 1000 - 4 = 996.00
            assertThat(s.getTarget2()).isEqualByComparingTo("996.00");
        }

        @Test
        @DisplayName("returns empty when gap-up is below 0.5% (condition 1 fails)")
        void detect_returnsEmpty_whenGapTooSmall() {
            // prevClose=1000, todayOpen=1003 → gapPct=0.3% < 0.5%
            Candle first = candle("09:15:00", 1003.0, 1005.0, 1001.0, 1004.0, 21000);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when gap-up exceeds 3.0% (condition 1 fails)")
        void detect_returnsEmpty_whenGapTooLarge() {
            // prevClose=1000, todayOpen=1040 → gapPct=4.0% > 3.0%
            Candle first = candle("09:15:00", 1040.0, 1045.0, 1038.0, 1041.0, 21000);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when volume is below 2.0× baseline (condition 2 fails)")
        void detect_returnsEmpty_whenVolumeInsufficient() {
            Candle first = candle("09:15:00", 1010.0, 1015.0, 1008.0, 1011.0, 18000); // 18000 < 20000
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 15), 10000L);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), baselines, "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when candle is after 10:30 IST (outside window)")
        void detect_returnsEmpty_whenCandleAfterWindow() {
            Candle first = candle("10:31:00", 1010.0, 1015.0, 1008.0, 1011.0, 21000);

            Optional<Signal> result = detector.detectWithPrevClose(
                    stock, List.of(first), Map.of(), "SIDEWAYS", bd(1000.0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("BROKER_NOTE constant is set correctly")
        void brokerNote_isSetCorrectly() {
            assertThat(GapFillShortDetector.BROKER_NOTE)
                    .contains("intraday short-selling")
                    .contains("MIS");
        }

        @Test
        @DisplayName("maxSignalsPerDay returns 1")
        void maxSignalsPerDay_returns1() {
            assertThat(detector.maxSignalsPerDay()).isEqualTo(1);
        }
    }
}
