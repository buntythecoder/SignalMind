package pl.piomin.signalmind.signal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.signal.detector.RsiOverboughtRejectionDetector;
import pl.piomin.signalmind.signal.detector.RsiOversoldBounceDetector;
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
 * Pure unit tests for {@link RsiOversoldBounceDetector} and
 * {@link RsiOverboughtRejectionDetector} (SM-24).
 *
 * <p>No Spring context is loaded.  All candle lists are supplied oldest-first
 * to match the detector contract.
 */
class RsiReversalDetectorTest {

    private static final ZoneId IST  = ZoneId.of("Asia/Kolkata");
    private static final String DATE = "2025-01-15";

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = new Stock("HDFC", "HDFC Bank Ltd", IndexType.NIFTY50);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    private static Instant ist(String time) {
        return LocalDateTime.parse(DATE + "T" + time).atZone(IST).toInstant();
    }

    /**
     * Builds a real candle with explicit OHLC, VWAP, and RSI.
     *
     * @param time      HH:mm:ss in IST
     * @param open      open price
     * @param high      high price
     * @param low       low price
     * @param close     close price
     * @param vwap      VWAP value
     * @param rsi       RSI(14)
     * @param volume    candle volume
     */
    private Candle candle(String time, double open, double high, double low, double close,
                          double vwap, double rsi, long volume) {
        return new Candle(stock, ist(time),
                bd(open), bd(high), bd(low), bd(close),
                volume, CandleSource.LIVE,
                bd(vwap), null, null, bd(rsi));
    }

    private static BigDecimal bd(double v) {
        return new BigDecimal(String.valueOf(v));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RsiOversoldBounceDetector
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RsiOversoldBounceDetector")
    class OversoldBounceTests {

        private RsiOversoldBounceDetector detector;

        @BeforeEach
        void setUp() {
            detector = new RsiOversoldBounceDetector();
        }

        // ── Happy path ────────────────────────────────────────────────────────

        @Test
        @DisplayName("returns LONG signal when RSI crosses above 25 with all conditions met")
        void detect_returnsLong_whenAllConditionsMet() {
            // prev: RSI=22 < 25 ✓; low=98.0 (will be SL)
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.0, 99.0,  100.0, 22.0, 5000);
            // curr: RSI=27 > 25 AND 27>22 (rising) ✓; close=100.2 within 1% of VWAP=100 ✓
            //       entry=100.20, SL=98.00, risk=2.20; T1=VWAP=100, reward=T1-entry=-0.20 → negative
            // Need T1 > entry for bounce: close must be BELOW VWAP for R:R to work
            // Let me restructure: price is below VWAP on bounce
            // curr: close=99.2, vwap=100.0; reward = 100-99.2=0.8; SL = prev.low=98.0; risk=99.2-98.0=1.2
            // R:R = 0.8/1.2 = 0.67 < 1.5 → would fail R:R
            // Need: reward/risk >= 1.5 → reward = 1.5 × risk
            // Entry=99.5, SL=98.0, risk=1.5; T1=VWAP=102.5, reward=3.0; R:R=2.0 ✓
            Candle p = candle("10:00:00", 100.0, 100.5, 98.0, 99.0, 102.5, 22.0, 5000);
            // close=99.5, vwap=102.5 → proximity=(102.5-99.5)/102.5=2.93% > 1% → would fail prox
            // The price must be within 1% of VWAP, so close ≈ VWAP ± 1%
            // VWAP=100, close=99.5 → prox=0.5% ✓; SL=prev.low=97.5; risk=2.0; T1=100; reward=0.5 → R:R=0.25 → fail
            // The bounce scenario: price bounces from oversold, still below VWAP but approaching it
            // For R:R=1.5: reward >= 1.5×risk; T1-entry >= 1.5×(entry-SL)
            // VWAP=100.5, entry=99.5 → prox=1.0% ✓; reward=1.0; SL=prev.low=98.5; risk=1.0; R:R=1.0 → fail
            // VWAP=102.0, entry=101.0 → prox=0.98% ✓; SL=97.0; risk=4.0; reward=1.0; R:R=0.25 → fail
            // Need: price far below VWAP? But prox must be ≤ 1%...
            // Wait — the price is within 1% of VWAP means the close is near VWAP (approaching it after bounce)
            // For R:R to work: T1 (VWAP) must be much higher than entry; but entry must be within 1% of VWAP
            // This means R:R = (VWAP-entry)/(entry-SL)
            // Entry within 1%: VWAP × 0.99 ≤ entry ≤ VWAP × 1.01
            // Max reward = VWAP - entry ≈ 0.01 × VWAP (only 1%)
            // For R:R=1.5: risk = reward/1.5 ≤ 0.01×VWAP/1.5 ≈ 0.0067×VWAP
            // SL = entry - risk ≈ VWAP × (0.99 - 0.0067) = VWAP × 0.9833
            // So prev.low must be ≤ VWAP × 0.9833
            // Example: VWAP=100, entry=99.0 (prox=1.0%✓), SL=prev.low=97.5, risk=1.5; reward=1.0; R:R=0.67 → fail
            // To get R:R=1.5 with prox 1%: need risk ≤ reward/1.5 = 1.0/1.5 = 0.67
            // SL = entry - 0.67 = 98.33; prev.low=98.33 → risk=0.67; reward=1.0; R:R=1.5 ✓!

            // Test setup: VWAP=100, entry=99.0, prev.low=98.5, risk=0.5, T1=100, reward=1.0, R:R=2.0 ✓
            Candle prevCandle = candle("10:00:00", 100.0, 100.2, 98.5, 99.5, 100.0, 22.0, 5000);
            // curr: close=99.0, vwap=100; prox=(100-99)/100=1.0% ✓; RSI=28>25 AND 28>22 ✓
            Candle currCandle = candle("10:01:00", 99.0, 99.5, 98.8, 99.0, 100.0, 28.0, 8000);

            // volume: 8000 >= 1.5 × 5000 = 7500 ✓
            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(10, 1), 5000L);

            Optional<Signal> result = detector.detect(stock, List.of(prevCandle, currCandle),
                    baselines, "SIDEWAYS");

            assertThat(result).isPresent();
            Signal s = result.get();
            assertThat(s.getSignalType()).isEqualTo(SignalType.RSI_OVERSOLD_BOUNCE);
            assertThat(s.getDirection()).isEqualTo(SignalDirection.LONG);
            // entry = curr.close = 99.00
            assertThat(s.getEntryPrice()).isEqualByComparingTo("99.00");
            // SL = prev.low = 98.50
            assertThat(s.getStopLoss()).isEqualByComparingTo("98.50");
            // T1 = VWAP = 100.00; risk=0.5, reward=1.0, R:R=2.0 ✓
            assertThat(s.getTargetPrice()).isEqualByComparingTo("100.00");
        }

        // ── Condition 1: prev RSI must be < 25 ───────────────────────────────

        @Test
        @DisplayName("returns empty when prev RSI is NOT below 25 (condition 1 fails)")
        void detect_returnsEmpty_whenPrevRsiNotOversold() {
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.5, 99.5, 100.0, 26.0, 5000); // RSI=26≥25
            Candle curr = candle("10:01:00", 99.0, 99.5, 98.8, 99.0, 100.0, 28.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Condition 2: curr RSI must cross above 25 and be rising ──────────

        @Test
        @DisplayName("returns empty when curr RSI is still below 25 (condition 2 fails)")
        void detect_returnsEmpty_whenCurrRsiStillOversold() {
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.5, 99.5, 100.0, 22.0, 5000);
            Candle curr = candle("10:01:00", 99.0, 99.5, 98.8, 99.0, 100.0, 24.0, 8000); // RSI=24<25

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when curr RSI is not rising (condition 2 fails)")
        void detect_returnsEmpty_whenRsiNotRising() {
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.5, 99.5, 100.0, 22.0, 5000);
            // curr RSI=22 — crossed threshold but same as prev (not rising)
            Candle curr = candle("10:01:00", 99.0, 99.5, 98.8, 99.0, 100.0, 22.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Condition 3: price within 1% of VWAP ─────────────────────────────

        @Test
        @DisplayName("returns empty when close is more than 1% away from VWAP (condition 3 fails)")
        void detect_returnsEmpty_whenPriceTooFarFromVwap() {
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.5, 99.5, 100.0, 22.0, 5000);
            // close=97.0, vwap=100.0 → prox=3.0% > 1%
            Candle curr = candle("10:01:00", 97.0, 97.5, 96.5, 97.0, 100.0, 28.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Condition 4: volume >= 1.5× baseline ─────────────────────────────

        @Test
        @DisplayName("returns empty when volume is below 1.5× baseline (condition 4 fails)")
        void detect_returnsEmpty_whenVolumeInsufficient() {
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.5, 99.5, 100.0, 22.0, 5000);
            Candle curr = candle("10:01:00", 99.0, 99.5, 98.8, 99.0, 100.0, 28.0, 7000); // 7000 < 7500

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Condition 5: regime must not be TRENDING_DOWN ─────────────────────

        @Test
        @DisplayName("returns empty when regime is TRENDING_DOWN (condition 5 fails)")
        void detect_returnsEmpty_whenRegimeTrendingDown() {
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.5, 99.5, 100.0, 22.0, 5000);
            Candle curr = candle("10:01:00", 99.0, 99.5, 98.8, 99.0, 100.0, 28.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "TRENDING_DOWN");

            assertThat(result).isEmpty();
        }

        // ── Time window ───────────────────────────────────────────────────────

        @Test
        @DisplayName("returns empty when curr candle is before 10:00 IST (outside window)")
        void detect_returnsEmpty_whenBeforeWindow() {
            Candle prev = candle("09:58:00", 100.0, 100.5, 98.5, 99.5, 100.0, 22.0, 5000);
            Candle curr = candle("09:59:00", 99.0, 99.5, 98.8, 99.0, 100.0, 28.0, 8000); // 09:59 < 10:00

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(9, 59), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when curr candle is after 14:30 IST (outside window)")
        void detect_returnsEmpty_whenAfterWindow() {
            Candle prev = candle("14:30:00", 100.0, 100.5, 98.5, 99.5, 100.0, 22.0, 5000);
            Candle curr = candle("14:31:00", 99.0, 99.5, 98.8, 99.0, 100.0, 28.0, 8000); // 14:31 > 14:30

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(14, 31), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── R:R check ─────────────────────────────────────────────────────────

        @Test
        @DisplayName("returns empty when R:R is below 1.5")
        void detect_returnsEmpty_whenRrBelowMinimum() {
            // entry=99.0, SL=prev.low=98.5, risk=0.5; T1=VWAP=99.4, reward=0.4; R:R=0.8 < 1.5
            Candle prev = candle("10:00:00", 100.0, 100.5, 98.5, 99.5, 99.4, 22.0, 5000);
            Candle curr = candle("10:01:00", 99.0, 99.5, 98.8, 99.0, 99.4, 28.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Metadata checks ───────────────────────────────────────────────────

        @Test
        @DisplayName("maxSignalsPerDay returns 2")
        void maxSignalsPerDay_returns2() {
            assertThat(detector.maxSignalsPerDay()).isEqualTo(2);
        }

        @Test
        @DisplayName("countedTypes contains only RSI_OVERSOLD_BOUNCE (no shared cap)")
        void countedTypes_containsOnlyOwnType() {
            assertThat(detector.countedTypes())
                    .containsExactly(SignalType.RSI_OVERSOLD_BOUNCE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RsiOverboughtRejectionDetector
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RsiOverboughtRejectionDetector")
    class OverboughtRejectionTests {

        private RsiOverboughtRejectionDetector detector;

        @BeforeEach
        void setUp() {
            detector = new RsiOverboughtRejectionDetector();
        }

        // ── Happy path ────────────────────────────────────────────────────────

        @Test
        @DisplayName("returns SHORT signal when RSI crosses below 75 with all conditions met")
        void detect_returnsShort_whenAllConditionsMet() {
            // prev: RSI=78 > 75 ✓; high=101.5 (will be SL)
            // curr: close=101.0, vwap=100.0; prox=(101-100)/100=1.0% ✓; RSI=72<75 AND 72<78 ✓
            // entry=101.00, SL=prev.high=101.5, risk=0.5; T1=VWAP=100.0, reward=1.0; R:R=2.0 ✓
            // volume: 8000 >= 1.5 × 5000 = 7500 ✓
            Candle prev = candle("10:00:00", 101.0, 101.5, 100.8, 101.2, 100.0, 78.0, 5000);
            Candle curr = candle("10:01:00", 101.1, 101.3, 100.9, 101.0, 100.0, 72.0, 8000);

            Map<LocalTime, Long> baselines = Map.of(LocalTime.of(10, 1), 5000L);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr), baselines, "SIDEWAYS");

            assertThat(result).isPresent();
            Signal s = result.get();
            assertThat(s.getSignalType()).isEqualTo(SignalType.RSI_OVERBOUGHT_REJECTION);
            assertThat(s.getDirection()).isEqualTo(SignalDirection.SHORT);
            // entry = curr.close = 101.00
            assertThat(s.getEntryPrice()).isEqualByComparingTo("101.00");
            // SL = prev.high = 101.50
            assertThat(s.getStopLoss()).isEqualByComparingTo("101.50");
            // T1 = VWAP = 100.00; reward=1.0, risk=0.5, R:R=2.0 ✓
            assertThat(s.getTargetPrice()).isEqualByComparingTo("100.00");
        }

        // ── Condition 1: prev RSI must be > 75 ───────────────────────────────

        @Test
        @DisplayName("returns empty when prev RSI is NOT above 75 (condition 1 fails)")
        void detect_returnsEmpty_whenPrevRsiNotOverbought() {
            Candle prev = candle("10:00:00", 101.0, 101.5, 100.8, 101.2, 100.0, 74.0, 5000); // RSI=74≤75
            Candle curr = candle("10:01:00", 101.1, 101.3, 100.9, 101.0, 100.0, 72.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Condition 2: curr RSI must cross below 75 and be falling ─────────

        @Test
        @DisplayName("returns empty when curr RSI is still above 75 (condition 2 fails)")
        void detect_returnsEmpty_whenCurrRsiStillOverbought() {
            Candle prev = candle("10:00:00", 101.0, 101.5, 100.8, 101.2, 100.0, 78.0, 5000);
            Candle curr = candle("10:01:00", 101.1, 101.3, 100.9, 101.0, 100.0, 76.0, 8000); // RSI=76>75

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when curr RSI is not falling (condition 2 fails)")
        void detect_returnsEmpty_whenRsiNotFalling() {
            Candle prev = candle("10:00:00", 101.0, 101.5, 100.8, 101.2, 100.0, 78.0, 5000);
            Candle curr = candle("10:01:00", 101.1, 101.3, 100.9, 101.0, 100.0, 78.0, 8000); // same RSI (not falling)

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Condition 3: price within 1% of VWAP ─────────────────────────────

        @Test
        @DisplayName("returns empty when close is more than 1% away from VWAP (condition 3 fails)")
        void detect_returnsEmpty_whenPriceTooFarFromVwap() {
            Candle prev = candle("10:00:00", 101.0, 101.5, 100.8, 101.2, 100.0, 78.0, 5000);
            // close=103.5, vwap=100.0 → prox=3.5% > 1%
            Candle curr = candle("10:01:00", 103.0, 103.5, 102.5, 103.5, 100.0, 72.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Condition 5: regime must not be TRENDING_UP ───────────────────────

        @Test
        @DisplayName("returns empty when regime is TRENDING_UP (condition 5 fails)")
        void detect_returnsEmpty_whenRegimeTrendingUp() {
            Candle prev = candle("10:00:00", 101.0, 101.5, 100.8, 101.2, 100.0, 78.0, 5000);
            Candle curr = candle("10:01:00", 101.1, 101.3, 100.9, 101.0, 100.0, 72.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "TRENDING_UP");

            assertThat(result).isEmpty();
        }

        // ── Time window ───────────────────────────────────────────────────────

        @Test
        @DisplayName("returns empty when curr candle is before 10:00 IST (outside window)")
        void detect_returnsEmpty_whenBeforeWindow() {
            Candle prev = candle("09:58:00", 101.0, 101.5, 100.8, 101.2, 100.0, 78.0, 5000);
            Candle curr = candle("09:59:00", 101.1, 101.3, 100.9, 101.0, 100.0, 72.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(9, 59), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when curr candle is after 14:30 IST (outside window)")
        void detect_returnsEmpty_whenAfterWindow() {
            Candle prev = candle("14:30:00", 101.0, 101.5, 100.8, 101.2, 100.0, 78.0, 5000);
            Candle curr = candle("14:31:00", 101.1, 101.3, 100.9, 101.0, 100.0, 72.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(14, 31), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── R:R check ─────────────────────────────────────────────────────────

        @Test
        @DisplayName("returns empty when R:R is below 1.5")
        void detect_returnsEmpty_whenRrBelowMinimum() {
            // entry=101.0, SL=prev.high=101.5, risk=0.5; T1=VWAP=100.7, reward=0.3; R:R=0.6 < 1.5
            Candle prev = candle("10:00:00", 101.0, 101.5, 100.8, 101.2, 100.7, 78.0, 5000);
            Candle curr = candle("10:01:00", 101.1, 101.3, 100.9, 101.0, 100.7, 72.0, 8000);

            Optional<Signal> result = detector.detect(stock, List.of(prev, curr),
                    Map.of(LocalTime.of(10, 1), 5000L), "SIDEWAYS");

            assertThat(result).isEmpty();
        }

        // ── Metadata checks ───────────────────────────────────────────────────

        @Test
        @DisplayName("maxSignalsPerDay returns 2")
        void maxSignalsPerDay_returns2() {
            assertThat(detector.maxSignalsPerDay()).isEqualTo(2);
        }

        @Test
        @DisplayName("countedTypes contains only RSI_OVERBOUGHT_REJECTION (no shared cap)")
        void countedTypes_containsOnlyOwnType() {
            assertThat(detector.countedTypes())
                    .containsExactly(SignalType.RSI_OVERBOUGHT_REJECTION);
        }
    }
}
