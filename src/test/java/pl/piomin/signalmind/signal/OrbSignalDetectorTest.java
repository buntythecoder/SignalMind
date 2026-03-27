package pl.piomin.signalmind.signal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.signal.detector.OrbSignalDetector;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link OrbSignalDetector} (SM-22).
 *
 * <p>No Spring context is loaded — the detector is constructed directly via
 * {@code new OrbSignalDetector()}.  All candle lists are supplied oldest-first
 * to match the contract expected by the detector.
 */
class OrbSignalDetectorTest {

    private static final ZoneId IST  = ZoneId.of("Asia/Kolkata");
    private static final String DATE = "2025-01-15";

    private OrbSignalDetector detector;
    private Stock stock;

    @BeforeEach
    void setUp() {
        detector = new OrbSignalDetector();
        stock = new Stock("RELIANCE", "Reliance Industries Ltd", IndexType.NIFTY50);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    /** Converts a HH:mm:ss time string on the test date to an IST-anchored Instant. */
    private static Instant ist(String time) {
        return LocalDateTime.parse(DATE + "T" + time).atZone(IST).toInstant();
    }

    /** Builds a real (non-synthetic) LIVE candle with explicit OHLCV. */
    private Candle candle(Stock s, String time, double o, double h, double l, double c, long vol) {
        return new Candle(s, ist(time),
                new BigDecimal(String.valueOf(o)),
                new BigDecimal(String.valueOf(h)),
                new BigDecimal(String.valueOf(l)),
                new BigDecimal(String.valueOf(c)),
                vol, CandleSource.LIVE,
                null, null, null, null);
    }

    /**
     * Builds a list of OR candles from 09:15 to 09:29 (15 candles) with the specified
     * opening-range high and low.  Intermediate candles are flat at the mid-price.
     * ORH and ORL are achieved by the first and last candles respectively.
     */
    private List<Candle> buildOrCandles(Stock s, double orh, double orl) {
        List<Candle> candles = new ArrayList<>();
        double mid = (orh + orl) / 2.0;
        // first candle hits the high
        candles.add(candle(s, "09:15:00", orl, orh, orl, mid, 500));
        for (int minute = 16; minute <= 28; minute++) {
            String t = String.format("09:%02d:00", minute);
            candles.add(candle(s, t, mid, mid, mid, mid, 400));
        }
        // last OR candle touches the low
        candles.add(candle(s, "09:29:00", mid, mid, orl, orl, 600));
        return candles;
    }

    // ── Test 1: LONG signal on bullish breakout with sufficient volume ────────

    @Test
    @DisplayName("detect returns LONG signal when close > ORH with sufficient volume")
    void detect_returnsLongSignal_whenCloseAboveOrh_withSufficientVolume() {
        // ORH=102, ORL=100 → ORW=2, midPrice=101, ORW%≈1.98% < 2.5% ✓
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 102.0, 100.0));

        // Breakout candle at 09:35: close=102.5 > ORH=102, volume=1600 vs baseline=1000 (1.6× ≥ 1.5×)
        candles.add(candle(stock, "09:35:00", 101.5, 103.0, 101.0, 102.5, 1600));

        Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 35), 1000L);

        Optional<Signal> result = detector.detect(stock, candles, baselines, "SIDEWAYS");

        assertThat(result).isPresent();
        Signal s = result.get();
        assertThat(s.getSignalType()).isEqualTo(SignalType.ORB);
        assertThat(s.getDirection()).isEqualTo(SignalDirection.LONG);
        assertThat(s.getEntryPrice()).isEqualByComparingTo("102.00");
        assertThat(s.getStopLoss()).isEqualByComparingTo("100.00");
        assertThat(s.getTargetPrice()).isEqualByComparingTo("104.00");   // T1 = 102 + 2
        assertThat(s.getTarget2()).isEqualByComparingTo("106.00");        // T2 = 102 + 4
    }

    // ── Test 2: SHORT signal on bearish breakout with sufficient volume ───────

    @Test
    @DisplayName("detect returns SHORT signal when close < ORL with sufficient volume")
    void detect_returnsShortSignal_whenCloseBelowOrl_withSufficientVolume() {
        // ORH=102, ORL=100
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 102.0, 100.0));

        // Breakout candle at 09:40: close=99.5 < ORL=100, volume=1600 vs baseline=1000
        candles.add(candle(stock, "09:40:00", 100.5, 101.0, 99.0, 99.5, 1600));

        Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 40), 1000L);

        Optional<Signal> result = detector.detect(stock, candles, baselines, "SIDEWAYS");

        assertThat(result).isPresent();
        Signal s = result.get();
        assertThat(s.getDirection()).isEqualTo(SignalDirection.SHORT);
        assertThat(s.getEntryPrice()).isEqualByComparingTo("100.00");
        assertThat(s.getStopLoss()).isEqualByComparingTo("102.00");
        assertThat(s.getTargetPrice()).isEqualByComparingTo("98.00");    // T1 = 100 - 2
        assertThat(s.getTarget2()).isEqualByComparingTo("96.00");         // T2 = 100 - 4
    }

    // ── Test 3: Reject when OR width exceeds 2.5% ────────────────────────────

    @Test
    @DisplayName("detect returns empty when opening-range width exceeds 2.5%")
    void detect_returnsEmpty_whenOrwTooWide() {
        // ORH=121.0, ORL=100.0 → ORW=21, midPrice=110.5, ORW%=19.0% >> 2.5%
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 121.0, 100.0));
        candles.add(candle(stock, "09:35:00", 120.0, 122.0, 119.0, 122.0, 2000));

        Optional<Signal> result = detector.detect(stock, candles, Map.of(), "SIDEWAYS");

        assertThat(result).isEmpty();
    }

    // ── Test 4: Reject when breakout volume is insufficient ──────────────────

    @Test
    @DisplayName("detect returns empty when breakout candle volume < 1.5× baseline")
    void detect_returnsEmpty_whenVolumeInsufficient() {
        // ORH=102, ORL=100 → ORW%≈1.98% passes the width check
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 102.0, 100.0));

        // close=102.5 > ORH, but volume=1000 vs baseline=1000 → 1.0× < 1.5× → REJECT
        candles.add(candle(stock, "09:35:00", 101.5, 103.0, 101.0, 102.5, 1000));

        Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 35), 1000L);

        Optional<Signal> result = detector.detect(stock, candles, baselines, "SIDEWAYS");

        assertThat(result).isEmpty();
    }

    // ── Test 5: Reject breakout candle after 11:30 IST ───────────────────────

    @Test
    @DisplayName("detect returns empty when breakout candle is after 11:30 IST")
    void detect_returnsEmpty_whenBreakoutAfterWindow() {
        // ORH=102, ORL=100
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 102.0, 100.0));

        // Candle at 11:31 IST — outside the [09:30, 11:30] window
        candles.add(candle(stock, "11:31:00", 101.5, 103.0, 101.0, 102.5, 2000));

        Optional<Signal> result = detector.detect(stock, candles, Map.of(), "SIDEWAYS");

        assertThat(result).isEmpty();
    }

    // ── Test 6: Reject flat opening range (ORH == ORL) ───────────────────────

    @Test
    @DisplayName("detect returns empty when opening range is flat (ORH == ORL)")
    void detect_returnsEmpty_whenFlatOpeningRange() {
        // All OR candles have OHLC = 100.0 → ORH == ORL → divide-by-zero guard triggers
        List<Candle> candles = new ArrayList<>();
        for (int minute = 15; minute <= 29; minute++) {
            String t = String.format("09:%02d:00", minute);
            candles.add(candle(stock, t, 100.0, 100.0, 100.0, 100.0, 500));
        }
        candles.add(candle(stock, "09:35:00", 100.0, 101.0, 99.0, 100.5, 2000));

        Optional<Signal> result = detector.detect(stock, candles, Map.of(), "SIDEWAYS");

        assertThat(result).isEmpty();
    }

    // ── Test 7: Synthetic candle in breakout window must not trigger a signal ─

    @Test
    @DisplayName("detect skips synthetic candles in breakout window; fires on first real candle above ORH")
    void detect_skipsSyntheticCandleForBreakout() {
        // ORH=102, ORL=100
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 102.0, 100.0));

        // Synthetic candle at 09:32 with close=103 (> ORH) — must be IGNORED
        Candle synthetic = Candle.synthetic(
                stock, ist("09:32:00"),
                new BigDecimal("103.00"),
                new BigDecimal("102.00"),
                new BigDecimal("102.50"),
                new BigDecimal("102.30"));
        candles.add(synthetic);

        // Real candle at 09:33 with close=102.5 (> ORH=102), volume=2000 (no baseline → skipped) — SHOULD trigger
        Candle real = candle(stock, "09:33:00", 101.0, 103.5, 100.5, 102.5, 2000);
        candles.add(real);

        // Sort oldest-first (synthetic is at 09:32, real at 09:33)
        candles.sort(java.util.Comparator.comparing(Candle::getCandleTime));

        Optional<Signal> result = detector.detect(stock, candles, Map.of(), "SIDEWAYS");

        assertThat(result).isPresent();
        assertThat(result.get().getGeneratedAt()).isEqualTo(ist("09:33:00"));
        assertThat(result.get().getDirection()).isEqualTo(SignalDirection.LONG);
    }

    // ── Test 8: No signal when all breakout-window candles stay inside OR ────

    @Test
    @DisplayName("detect returns empty when no breakout candle closes outside the opening range")
    void detect_returnsEmpty_whenNoBreakoutInWindow() {
        // ORH=102, ORL=100
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 102.0, 100.0));

        // Breakout window: all closes between 100 and 102 — no breakout
        candles.add(candle(stock, "09:32:00", 100.5, 101.8, 100.2, 101.5, 2000));
        candles.add(candle(stock, "09:33:00", 101.5, 102.0, 100.5, 100.8, 2000));
        candles.add(candle(stock, "09:34:00", 100.8, 101.9, 100.3, 101.0, 2000));
        candles.add(candle(stock, "10:00:00", 101.0, 101.5, 100.0, 101.2, 2000));
        candles.add(candle(stock, "11:00:00", 101.2, 101.9, 100.1, 100.9, 2000));

        Optional<Signal> result = detector.detect(stock, candles, Map.of(), "SIDEWAYS");

        assertThat(result).isEmpty();
    }

    // ── Bonus: Regime modifier is applied to confidence ───────────────────────

    @Test
    @DisplayName("detect applies TRENDING_UP regime modifier (+15) to base confidence of 50")
    void detect_appliesRegimeModifierToConfidence() {
        List<Candle> candles = new ArrayList<>(buildOrCandles(stock, 102.0, 100.0));
        candles.add(candle(stock, "09:35:00", 101.5, 103.0, 101.0, 102.5, 1600));

        Map<LocalTime, Long> baselines = Map.of(LocalTime.of(9, 35), 1000L);

        Optional<Signal> result = detector.detect(stock, candles, baselines, "TRENDING_UP");

        assertThat(result).isPresent();
        // 50 (base) + 15 (TRENDING_UP modifier) = 65
        assertThat(result.get().getConfidence()).isEqualTo(65);
    }
}
