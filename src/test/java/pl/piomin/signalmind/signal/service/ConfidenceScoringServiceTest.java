package pl.piomin.signalmind.signal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ConfidenceScoringService} (SM-26).
 *
 * <p>Each of the 6 scoring factors is tested in its own {@code @Nested} class.
 * The confluence factor uses a mocked {@link SignalRepository}.
 */
class ConfidenceScoringServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String DATE = "2025-01-15";

    private SignalRepository mockRepo;
    private ConfidenceScoringService service;
    private Stock stock;

    @BeforeEach
    void setUp() {
        mockRepo = mock(SignalRepository.class);
        service  = new ConfidenceScoringService(mockRepo);
        stock    = new Stock("TCS", "Tata Consultancy Services", IndexType.NIFTY50);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Instant ist(String time) {
        return LocalDateTime.parse(DATE + "T" + time).atZone(IST).toInstant();
    }

    private Signal minimalSignal(SignalType type, SignalDirection direction,
                                  String regime, Instant generatedAt) {
        return new Signal(
                stock, type, direction,
                new BigDecimal("1000.00"),
                new BigDecimal("1010.00"),
                new BigDecimal("1020.00"),
                new BigDecimal("995.00"),
                50, regime,
                generatedAt,
                generatedAt.plusSeconds(900),
                null, null
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Factor 1 — Base score
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("scoreBase")
    class BaseTests {

        @Test
        @DisplayName("always returns 50")
        void scoreBase_always50() {
            assertThat(service.scoreBase()).isEqualTo(50);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Factor 2 — Volume confirmation
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("scoreVolume")
    class VolumeTests {

        @Test
        @DisplayName("returns 15 when volume >= 3.0× baseline")
        void volume_3x_returns15() {
            assertThat(service.scoreVolume(30000, 10000L)).isEqualTo(15);
        }

        @Test
        @DisplayName("returns 10 when volume >= 2.0× but < 3.0× baseline")
        void volume_2x_returns10() {
            assertThat(service.scoreVolume(20000, 10000L)).isEqualTo(10);
        }

        @Test
        @DisplayName("returns 5 when volume >= 1.5× but < 2.0× baseline")
        void volume_1_5x_returns5() {
            assertThat(service.scoreVolume(15000, 10000L)).isEqualTo(5);
        }

        @Test
        @DisplayName("returns 0 when volume < 1.5× baseline")
        void volume_below1_5x_returns0() {
            assertThat(service.scoreVolume(14000, 10000L)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when baseline is null")
        void volume_nullBaseline_returns0() {
            assertThat(service.scoreVolume(50000, null)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when baseline is zero")
        void volume_zeroBaseline_returns0() {
            assertThat(service.scoreVolume(50000, 0L)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 15 at exact 3.0× boundary")
        void volume_exact3x_returns15() {
            assertThat(service.scoreVolume(30000, 10000L)).isEqualTo(15);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Factor 3 — Time of day
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("scoreTimeOfDay")
    class TimeOfDayTests {

        @Test
        @DisplayName("returns 10 for 09:15–09:29 (opening window)")
        void time_openingWindow_returns10() {
            assertThat(service.scoreTimeOfDay(ist("09:15:00"))).isEqualTo(10);
            assertThat(service.scoreTimeOfDay(ist("09:20:00"))).isEqualTo(10);
            assertThat(service.scoreTimeOfDay(ist("09:29:00"))).isEqualTo(10);
        }

        @Test
        @DisplayName("returns 7 for 09:30–09:59 (morning active slot)")
        void time_morningActive_returns7() {
            assertThat(service.scoreTimeOfDay(ist("09:30:00"))).isEqualTo(7);
            assertThat(service.scoreTimeOfDay(ist("09:45:00"))).isEqualTo(7);
            assertThat(service.scoreTimeOfDay(ist("09:59:00"))).isEqualTo(7);
        }

        @Test
        @DisplayName("returns 5 for 10:00–10:59 (continuation window)")
        void time_continuation_returns5() {
            assertThat(service.scoreTimeOfDay(ist("10:00:00"))).isEqualTo(5);
            assertThat(service.scoreTimeOfDay(ist("10:30:00"))).isEqualTo(5);
        }

        @Test
        @DisplayName("returns 3 for afternoon / lunch lull")
        void time_afternoon_returns3() {
            assertThat(service.scoreTimeOfDay(ist("11:00:00"))).isEqualTo(3);
            assertThat(service.scoreTimeOfDay(ist("14:00:00"))).isEqualTo(3);
            assertThat(service.scoreTimeOfDay(ist("15:00:00"))).isEqualTo(3);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Factor 4 — Regime alignment
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("scoreRegime")
    class RegimeTests {

        @Test
        @DisplayName("LONG + TRENDING_UP → 10 (aligned)")
        void regime_longTrendingUp_returns10() {
            assertThat(service.scoreRegime(SignalDirection.LONG, "TRENDING_UP")).isEqualTo(10);
        }

        @Test
        @DisplayName("SHORT + TRENDING_DOWN → 10 (aligned)")
        void regime_shortTrendingDown_returns10() {
            assertThat(service.scoreRegime(SignalDirection.SHORT, "TRENDING_DOWN")).isEqualTo(10);
        }

        @Test
        @DisplayName("LONG + TRENDING_DOWN → 0 (against trend)")
        void regime_longTrendingDown_returns0() {
            assertThat(service.scoreRegime(SignalDirection.LONG, "TRENDING_DOWN")).isEqualTo(0);
        }

        @Test
        @DisplayName("SHORT + TRENDING_UP → 0 (against trend)")
        void regime_shortTrendingUp_returns0() {
            assertThat(service.scoreRegime(SignalDirection.SHORT, "TRENDING_UP")).isEqualTo(0);
        }

        @Test
        @DisplayName("SIDEWAYS → 5 regardless of direction")
        void regime_sideways_returns5() {
            assertThat(service.scoreRegime(SignalDirection.LONG,  "SIDEWAYS")).isEqualTo(5);
            assertThat(service.scoreRegime(SignalDirection.SHORT, "SIDEWAYS")).isEqualTo(5);
        }

        @Test
        @DisplayName("HIGH_VOLATILITY → 5 regardless of direction")
        void regime_highVolatility_returns5() {
            assertThat(service.scoreRegime(SignalDirection.LONG,  "HIGH_VOLATILITY")).isEqualTo(5);
            assertThat(service.scoreRegime(SignalDirection.SHORT, "HIGH_VOLATILITY")).isEqualTo(5);
        }

        @Test
        @DisplayName("null regime → 5 (neutral fallback)")
        void regime_null_returns5() {
            assertThat(service.scoreRegime(SignalDirection.LONG, null)).isEqualTo(5);
        }

        @Test
        @DisplayName("CIRCUIT_HALT → 0")
        void regime_circuitHalt_returns0() {
            assertThat(service.scoreRegime(SignalDirection.LONG,  "CIRCUIT_HALT")).isEqualTo(0);
            assertThat(service.scoreRegime(SignalDirection.SHORT, "CIRCUIT_HALT")).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Factor 5 — Historical win-rate proxy
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("scoreWinRate")
    class WinRateTests {

        @Test
        @DisplayName("ORB returns 8")
        void winRate_orb() {
            assertThat(service.scoreWinRate(SignalType.ORB)).isEqualTo(8);
        }

        @Test
        @DisplayName("GAP_FILL_LONG returns 8")
        void winRate_gapFillLong() {
            assertThat(service.scoreWinRate(SignalType.GAP_FILL_LONG)).isEqualTo(8);
        }

        @Test
        @DisplayName("VWAP_BREAKOUT returns 7")
        void winRate_vwapBreakout() {
            assertThat(service.scoreWinRate(SignalType.VWAP_BREAKOUT)).isEqualTo(7);
        }

        @Test
        @DisplayName("VWAP_BREAKDOWN returns 7")
        void winRate_vwapBreakdown() {
            assertThat(service.scoreWinRate(SignalType.VWAP_BREAKDOWN)).isEqualTo(7);
        }

        @Test
        @DisplayName("GAP_FILL_SHORT returns 7")
        void winRate_gapFillShort() {
            assertThat(service.scoreWinRate(SignalType.GAP_FILL_SHORT)).isEqualTo(7);
        }

        @Test
        @DisplayName("RSI_OVERSOLD_BOUNCE returns 6")
        void winRate_rsiOversoldBounce() {
            assertThat(service.scoreWinRate(SignalType.RSI_OVERSOLD_BOUNCE)).isEqualTo(6);
        }

        @Test
        @DisplayName("RSI_OVERBOUGHT_REJECTION returns 6")
        void winRate_rsiOverboughtRejection() {
            assertThat(service.scoreWinRate(SignalType.RSI_OVERBOUGHT_REJECTION)).isEqualTo(6);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Factor 6 — Multi-signal confluence
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("scoreConfluence")
    class ConfluenceTests {

        @Test
        @DisplayName("returns 0 when no prior signals in the window (first signal)")
        void confluence_noPriorSignals_returns0() {
            when(mockRepo.findByStockAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                    eq(stock), any(Instant.class), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            assertThat(service.scoreConfluence(stock, ist("10:00:00"))).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 10 when exactly 1 prior signal (second signal — confluence)")
        void confluence_onePriorSignal_returns10() {
            Signal prior = minimalSignal(SignalType.ORB, SignalDirection.LONG,
                    "SIDEWAYS", ist("09:58:00"));
            when(mockRepo.findByStockAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                    eq(stock), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(prior));

            assertThat(service.scoreConfluence(stock, ist("10:00:00"))).isEqualTo(10);
        }

        @Test
        @DisplayName("returns 15 when 2+ prior signals (third+ signal — strong confluence)")
        void confluence_twoPlusSignals_returns15() {
            Signal p1 = minimalSignal(SignalType.ORB, SignalDirection.LONG,
                    "SIDEWAYS", ist("09:56:00"));
            Signal p2 = minimalSignal(SignalType.VWAP_BREAKOUT, SignalDirection.LONG,
                    "SIDEWAYS", ist("09:58:00"));
            when(mockRepo.findByStockAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                    eq(stock), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(p1, p2));

            assertThat(service.scoreConfluence(stock, ist("10:00:00"))).isEqualTo(15);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Full score() integration — applyScores end-to-end
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("score() — end-to-end")
    class ScoreEndToEndTests {

        @BeforeEach
        void stubNoConfluence() {
            when(mockRepo.findByStockAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                    any(), any(), any()))
                    .thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("score() writes all 6 factors to the signal")
        void score_writesAllFactors() {
            Signal signal = minimalSignal(SignalType.ORB, SignalDirection.LONG,
                    "TRENDING_UP", ist("09:20:00"));

            service.score(signal, 30000L, 10000L); // 3× volume

            assertThat(signal.getScoreBase()).isEqualTo(50);
            assertThat(signal.getScoreVolume()).isEqualTo(15);     // 3× → 15
            assertThat(signal.getScoreTimeOfDay()).isEqualTo(10);  // 09:20 opening window
            assertThat(signal.getScoreRegime()).isEqualTo(10);     // LONG + TRENDING_UP
            assertThat(signal.getScoreWinRate()).isEqualTo(8);     // ORB
            assertThat(signal.getScoreConfluence()).isEqualTo(0);  // no prior signals
        }

        @Test
        @DisplayName("confidence = clamped sum of all 6 factors")
        void score_computesConfidenceAsCappedSum() {
            Signal signal = minimalSignal(SignalType.ORB, SignalDirection.LONG,
                    "TRENDING_UP", ist("09:20:00"));

            service.score(signal, 30000L, 10000L);

            // 50 + 15 + 10 + 10 + 8 + 0 = 93
            assertThat(signal.getConfidence()).isEqualTo(93);
        }

        @Test
        @DisplayName("confidence is capped at 100 even if factor sum exceeds it")
        void score_capsAt100() {
            // Manufacture a scenario where sum > 100:
            // Base=50 + Vol=15 + TOD=10 + Regime=10 + WinRate=8 + Confluence=15 = 108 → capped at 100
            Signal prior1 = minimalSignal(SignalType.ORB, SignalDirection.LONG,
                    "SIDEWAYS", ist("09:56:00"));
            Signal prior2 = minimalSignal(SignalType.VWAP_BREAKOUT, SignalDirection.LONG,
                    "SIDEWAYS", ist("09:58:00"));
            when(mockRepo.findByStockAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                    any(), any(), any()))
                    .thenReturn(List.of(prior1, prior2));

            Signal signal = minimalSignal(SignalType.ORB, SignalDirection.LONG,
                    "TRENDING_UP", ist("09:20:00"));
            service.score(signal, 30000L, 10000L);

            assertThat(signal.getConfidence()).isEqualTo(100);
        }

        @Test
        @DisplayName("score() with null baseline uses volume=0 (no volume bonus)")
        void score_nullBaseline_noVolumeBonus() {
            Signal signal = minimalSignal(SignalType.GAP_FILL_LONG, SignalDirection.LONG,
                    "SIDEWAYS", ist("09:30:00"));

            service.score(signal, 50000L, null); // null baseline → scoreVolume=0

            assertThat(signal.getScoreVolume()).isEqualTo(0);
            // 50 + 0 + 7 + 5 + 8 + 0 = 70
            assertThat(signal.getConfidence()).isEqualTo(70);
        }
    }
}
