package pl.piomin.signalmind.signal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalOutcome;
import pl.piomin.signalmind.signal.domain.SignalStatus;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.repository.SignalOutcomeRepository;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link OutcomeTrackerService} (SM-32).
 *
 * <p>No Spring context is loaded.  The service is constructed manually in
 * {@link #setUp()} so Redis is passed as {@code Optional.empty()}.
 * All tests exercise either the public {@link OutcomeTrackerService#getDailySummary} API
 * or the package-private {@link OutcomeTrackerService#recordOutcome} method.
 */
@ExtendWith(MockitoExtension.class)
class OutcomeTrackerServiceTest {

    @Mock
    SignalRepository        signalRepository;

    @Mock
    SignalOutcomeRepository outcomeRepository;

    @Mock
    CandleRepository        candleRepository;

    // Redis deliberately absent — tests run without Redis dependency
    OutcomeTrackerService service;

    private Stock stock;

    @BeforeEach
    void setUp() {
        service = new OutcomeTrackerService(
                signalRepository,
                outcomeRepository,
                candleRepository,
                Optional.empty()
        );
        stock = new Stock("RELIANCE", "Reliance Industries", IndexType.NIFTY50);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a minimal outcome with the given outcome code. */
    private SignalOutcome outcome(String code) {
        return new SignalOutcome(1L, code, BigDecimal.valueOf(100), Instant.now(), BigDecimal.ZERO);
    }

    /** Builds a LONG signal at the given prices with the given status. */
    private Signal longSignal(double entry, double target1, double stop, SignalStatus status) {
        Signal s = new Signal(
                stock,
                SignalType.ORB,
                SignalDirection.LONG,
                BigDecimal.valueOf(entry),
                BigDecimal.valueOf(target1),
                null,
                BigDecimal.valueOf(stop),
                75,
                "BULLISH",
                Instant.now().minusSeconds(300),
                Instant.now().plusSeconds(600),
                BigDecimal.valueOf(entry + 5),
                BigDecimal.valueOf(entry - 5)
        );
        s.updateStatus(status);
        return s;
    }

    /** Builds a SHORT signal at the given prices with the given status. */
    private Signal shortSignal(double entry, double target1, double stop, SignalStatus status) {
        Signal s = new Signal(
                stock,
                SignalType.ORB,
                SignalDirection.SHORT,
                BigDecimal.valueOf(entry),
                BigDecimal.valueOf(target1),
                null,
                BigDecimal.valueOf(stop),
                75,
                "BEARISH",
                Instant.now().minusSeconds(300),
                Instant.now().plusSeconds(600),
                BigDecimal.valueOf(entry + 5),
                BigDecimal.valueOf(entry - 5)
        );
        s.updateStatus(status);
        return s;
    }

    // ── 1. getDailySummary — correct counts ───────────────────────────────────

    @Test
    @DisplayName("getDailySummary: 3 wins, 1 loss, 1 expire → correct WinRateSummary")
    void getDailySummary_returnsCorrectCounts() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to   = Instant.now();

        when(outcomeRepository.findByRecordedAtBetween(from, to)).thenReturn(List.of(
                outcome("TARGET_1_HIT"),
                outcome("TARGET_2_HIT"),
                outcome("TARGET_1_HIT"),
                outcome("STOP_HIT"),
                outcome("EXPIRED")
        ));

        OutcomeTrackerService.WinRateSummary summary = service.getDailySummary(from, to);

        assertThat(summary.total()).isEqualTo(5);
        assertThat(summary.wins()).isEqualTo(3);
        assertThat(summary.losses()).isEqualTo(1);
        assertThat(summary.expires()).isEqualTo(1);
    }

    // ── 2. formattedWinRate — correct percentage ──────────────────────────────

    @Test
    @DisplayName("formattedWinRate: 3 wins out of 5 total → '60%'")
    void formattedWinRate_calculatesCorrectly() {
        OutcomeTrackerService.WinRateSummary summary =
                new OutcomeTrackerService.WinRateSummary(5, 3, 1, 1);

        assertThat(summary.formattedWinRate()).isEqualTo("60%");
    }

    // ── 3. formattedWinRate — zero total ──────────────────────────────────────

    @Test
    @DisplayName("formattedWinRate: 0 total outcomes → 'N/A'")
    void formattedWinRate_returnsNA_whenZeroTotal() {
        OutcomeTrackerService.WinRateSummary summary =
                new OutcomeTrackerService.WinRateSummary(0, 0, 0, 0);

        assertThat(summary.formattedWinRate()).isEqualTo("N/A");
    }

    // ── 4. recordOutcome — skip already-recorded ──────────────────────────────

    @Test
    @DisplayName("recordOutcome: outcome already exists for signal → no save call")
    void recordOutcome_skipsAlreadyRecorded() {
        Signal signal = longSignal(100.0, 110.0, 95.0, SignalStatus.TARGET_1_HIT);
        when(outcomeRepository.existsBySignalId(signal.getId())).thenReturn(true);

        service.recordOutcome(signal, BigDecimal.valueOf(110.0));

        verify(outcomeRepository, never()).save(any());
    }

    // ── 5. recordOutcome — TARGET_1_HIT LONG saves correct outcome ────────────

    @Test
    @DisplayName("recordOutcome: TARGET_1_HIT LONG signal → saves outcome with correct code and pnl")
    void recordOutcome_savesOutcome_forTargetHit() {
        Signal signal = longSignal(100.0, 110.0, 95.0, SignalStatus.TARGET_1_HIT);
        when(outcomeRepository.existsBySignalId(signal.getId())).thenReturn(false);

        service.recordOutcome(signal, BigDecimal.valueOf(110.0));

        ArgumentCaptor<SignalOutcome> captor = ArgumentCaptor.forClass(SignalOutcome.class);
        verify(outcomeRepository).save(captor.capture());

        SignalOutcome saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo("TARGET_1_HIT");
        // pnl = exitPrice(110) - entryPrice(100) = 10
        assertThat(saved.getPnlPoints()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    }

    // ── 6. recordOutcome — STOP_HIT LONG produces negative pnl ───────────────

    @Test
    @DisplayName("recordOutcome: STOP_HIT LONG signal → saves outcome with negative pnl")
    void recordOutcome_savesOutcome_forStopHit_long() {
        Signal signal = longSignal(100.0, 110.0, 95.0, SignalStatus.STOP_HIT);
        when(outcomeRepository.existsBySignalId(signal.getId())).thenReturn(false);

        // Exit price = stopLoss = 95
        service.recordOutcome(signal, BigDecimal.valueOf(95.0));

        ArgumentCaptor<SignalOutcome> captor = ArgumentCaptor.forClass(SignalOutcome.class);
        verify(outcomeRepository).save(captor.capture());

        SignalOutcome saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo("STOP_HIT");
        // pnl = exitPrice(95) - entryPrice(100) = -5
        assertThat(saved.getPnlPoints()).isEqualByComparingTo(BigDecimal.valueOf(-5.0));
    }
}
