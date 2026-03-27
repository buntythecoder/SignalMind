package pl.piomin.signalmind.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.market.service.TradingCalendarService;
import pl.piomin.signalmind.quality.domain.CandleIssue;
import pl.piomin.signalmind.quality.domain.QualityIssue;
import pl.piomin.signalmind.quality.service.MarketSessionGapDetector;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketSessionGapDetector}.
 * Uses Mockito to stub {@link TradingCalendarService}; no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class MarketSessionGapDetectorTest {

    @Mock
    private TradingCalendarService tradingCalendarService;

    private MarketSessionGapDetector detector;

    private static final LocalDate TRADING_DAY = LocalDate.of(2024, 1, 15);

    @BeforeEach
    void setUp() {
        detector = new MarketSessionGapDetector(tradingCalendarService);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a candle with valid OHLCV values at the given IST time on {@link #TRADING_DAY}.
     * Stock is null — the gap detector only inspects timestamps.
     */
    private Candle candleAt(LocalTime istTime) {
        Instant ts = TRADING_DAY.atTime(istTime)
                .atZone(MarketSessionGapDetector.IST)
                .toInstant();
        return new Candle(
                null,
                ts,
                BigDecimal.valueOf(100), BigDecimal.valueOf(102),
                BigDecimal.valueOf(98), BigDecimal.valueOf(101),
                1000L,
                CandleSource.HIST
        );
    }

    /**
     * Builds all 375 candles covering the full NSE session (09:15 to 15:29 IST).
     */
    private List<Candle> fullSessionCandles() {
        List<Candle> candles = new ArrayList<>(MarketSessionGapDetector.EXPECTED_CANDLES);
        ZonedDateTime cursor = TRADING_DAY.atTime(MarketSessionGapDetector.SESSION_START)
                .atZone(MarketSessionGapDetector.IST);
        ZonedDateTime end = TRADING_DAY.atTime(MarketSessionGapDetector.SESSION_END)
                .atZone(MarketSessionGapDetector.IST);
        while (!cursor.isAfter(end)) {
            candles.add(candleAt(cursor.toLocalTime()));
            cursor = cursor.plusMinutes(1);
        }
        return candles;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void tradingDay_allCandlesPresent_noGaps() {
        when(tradingCalendarService.isTradingDay(TRADING_DAY)).thenReturn(true);
        List<Candle> candles = fullSessionCandles();

        assertThat(candles).hasSize(MarketSessionGapDetector.EXPECTED_CANDLES);

        List<CandleIssue> issues = detector.validate("INFY", TRADING_DAY, candles);
        assertThat(issues).isEmpty();
    }

    @Test
    void tradingDay_missingCandle_detected() {
        when(tradingCalendarService.isTradingDay(TRADING_DAY)).thenReturn(true);

        // Build full session then remove the candle at 09:30.
        List<Candle> candles = new ArrayList<>(fullSessionCandles());
        candles.removeIf(c -> {
            ZonedDateTime zdt = c.getCandleTime().atZone(MarketSessionGapDetector.IST);
            return zdt.toLocalTime().equals(LocalTime.of(9, 30));
        });

        assertThat(candles).hasSize(MarketSessionGapDetector.EXPECTED_CANDLES - 1);

        List<CandleIssue> issues = detector.validate("INFY", TRADING_DAY, candles);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).issueType()).isEqualTo(QualityIssue.MISSING_CANDLE);
        assertThat(issues.get(0).detail()).contains("09:30");
    }

    @Test
    void weekend_skipped() {
        LocalDate saturday = LocalDate.of(2024, 1, 13); // Saturday
        when(tradingCalendarService.isTradingDay(saturday)).thenReturn(false);

        // Pass a non-empty candle list to verify the method exits early and ignores it.
        List<Candle> someCandles = List.of(candleAt(LocalTime.of(9, 15)));
        List<CandleIssue> issues = detector.validate("INFY", saturday, someCandles);

        assertThat(issues).isEmpty();
    }
}
