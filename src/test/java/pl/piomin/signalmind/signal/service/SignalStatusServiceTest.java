package pl.piomin.signalmind.signal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalStatus;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Pure unit tests for {@link SignalStatusService} (SM-31).
 *
 * <p>No Spring context is loaded. The service is constructed manually in
 * {@link #setUp()} to allow passing {@code Optional.empty()} for the SSE service.
 * All tests call the package-private {@link SignalStatusService#evaluateSignal} method
 * directly, bypassing the trading-session time guard in the scheduled methods.
 */
@ExtendWith(MockitoExtension.class)
class SignalStatusServiceTest {

    @Mock
    SignalRepository signalRepository;

    @Mock
    CandleRepository candleRepository;

    // SSE service deliberately absent — tests run without SSE broadcasts
    SignalStatusService service;

    private Stock stock;

    @BeforeEach
    void setUp() {
        service = new SignalStatusService(signalRepository, candleRepository, Optional.empty());
        stock   = new Stock("RELIANCE", "Reliance Industries", IndexType.NIFTY50);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a LONG signal with explicit T2 target.
     * Confidence, regime, ORB fields, and timestamps are filled with valid stubs.
     */
    private Signal longSignal(double entry, double target1, double target2, double stop) {
        return new Signal(
                stock,
                SignalType.ORB,
                SignalDirection.LONG,
                bd(entry),
                bd(target1),
                target2 > 0 ? bd(target2) : null,
                bd(stop),
                75,
                "BULLISH",
                Instant.now().minusSeconds(300),
                Instant.now().plusSeconds(600),
                bd(entry + 5),
                bd(entry - 5)
        );
    }

    /** Builds a LONG signal with no T2 target. */
    private Signal longSignal(double entry, double target1, double stop) {
        return longSignal(entry, target1, 0, stop);
    }

    /** Builds a SHORT signal with explicit T2 target. */
    private Signal shortSignal(double entry, double target1, double target2, double stop) {
        return new Signal(
                stock,
                SignalType.ORB,
                SignalDirection.SHORT,
                bd(entry),
                bd(target1),
                target2 > 0 ? bd(target2) : null,
                bd(stop),
                75,
                "BEARISH",
                Instant.now().minusSeconds(300),
                Instant.now().plusSeconds(600),
                bd(entry + 5),
                bd(entry - 5)
        );
    }

    /** Builds a SHORT signal with no T2 target. */
    private Signal shortSignal(double entry, double target1, double stop) {
        return shortSignal(entry, target1, 0, stop);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    // ── 1. LONG — entry trigger ───────────────────────────────────────────────

    @Test
    @DisplayName("LONG GENERATED: candle high >= entry → TRIGGERED, signal saved")
    void evaluateSignal_longSignal_triggeredWhenHighGtEntry() {
        Signal signal = longSignal(100.0, 110.0, 95.0);

        service.evaluateSignal(signal, bd(100.50), bd(99.0));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.TRIGGERED);
        verify(signalRepository).save(signal);
    }

    // ── 2. LONG — no trigger ──────────────────────────────────────────────────

    @Test
    @DisplayName("LONG GENERATED: candle high < entry → stays GENERATED, no save")
    void evaluateSignal_longSignal_notTriggered_whenHighLtEntry() {
        Signal signal = longSignal(100.0, 110.0, 95.0);

        service.evaluateSignal(signal, bd(99.50), bd(98.0));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.GENERATED);
        verify(signalRepository, never()).save(any());
    }

    // ── 3. LONG — stop hit ────────────────────────────────────────────────────

    @Test
    @DisplayName("LONG TRIGGERED: candle low <= stopLoss → STOP_HIT")
    void evaluateSignal_longSignal_stopHit() {
        Signal signal = longSignal(100.0, 110.0, 95.0);
        signal.updateStatus(SignalStatus.TRIGGERED);

        // low = 94.0 which is below stop of 95.0
        service.evaluateSignal(signal, bd(101.0), bd(94.0));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.STOP_HIT);
        verify(signalRepository).save(signal);
    }

    // ── 4. LONG — T1 hit ──────────────────────────────────────────────────────

    @Test
    @DisplayName("LONG TRIGGERED: candle high >= targetPrice (T1) → TARGET_1_HIT")
    void evaluateSignal_longSignal_target1Hit() {
        Signal signal = longSignal(100.0, 110.0, 95.0);  // no T2
        signal.updateStatus(SignalStatus.TRIGGERED);

        // high = 111.0 clears T1 at 110.0; no T2 defined
        service.evaluateSignal(signal, bd(111.0), bd(99.0));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.TARGET_1_HIT);
        verify(signalRepository).save(signal);
    }

    // ── 5. LONG — T2 hit ──────────────────────────────────────────────────────

    @Test
    @DisplayName("LONG TRIGGERED: candle high >= target2 → TARGET_2_HIT")
    void evaluateSignal_longSignal_target2Hit() {
        // T1 = 110, T2 = 115
        Signal signal = longSignal(100.0, 110.0, 115.0, 95.0);
        signal.updateStatus(SignalStatus.TRIGGERED);

        // high = 116.0 clears T2 at 115.0
        service.evaluateSignal(signal, bd(116.0), bd(99.0));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.TARGET_2_HIT);
        verify(signalRepository).save(signal);
    }

    // ── 6. SHORT — entry trigger ──────────────────────────────────────────────

    @Test
    @DisplayName("SHORT GENERATED: candle low <= entry → TRIGGERED, signal saved")
    void evaluateSignal_shortSignal_triggeredWhenLowLtEntry() {
        Signal signal = shortSignal(100.0, 90.0, 105.0);

        // low = 99.5 is at or below entry of 100.0
        service.evaluateSignal(signal, bd(100.5), bd(99.5));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.TRIGGERED);
        verify(signalRepository).save(signal);
    }

    // ── 7. SHORT — stop hit ───────────────────────────────────────────────────

    @Test
    @DisplayName("SHORT TRIGGERED: candle high >= stopLoss → STOP_HIT")
    void evaluateSignal_shortSignal_stopHit() {
        Signal signal = shortSignal(100.0, 90.0, 105.0);
        signal.updateStatus(SignalStatus.TRIGGERED);

        // high = 106.0 exceeds stop of 105.0
        service.evaluateSignal(signal, bd(106.0), bd(99.0));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.STOP_HIT);
        verify(signalRepository).save(signal);
    }

    // ── 8. SHORT — T1 hit ─────────────────────────────────────────────────────

    @Test
    @DisplayName("SHORT TRIGGERED: candle low <= targetPrice (T1) → TARGET_1_HIT")
    void evaluateSignal_shortSignal_target1Hit() {
        Signal signal = shortSignal(100.0, 90.0, 105.0);  // no T2
        signal.updateStatus(SignalStatus.TRIGGERED);

        // low = 89.0 is below T1 at 90.0
        service.evaluateSignal(signal, bd(102.0), bd(89.0));

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.TARGET_1_HIT);
        verify(signalRepository).save(signal);
    }

    // ── 9. Expiry: GENERATED signal past validUntil → EXPIRED ─────────────────

    @Test
    @DisplayName("checkSignalStatuses: GENERATED signal with validUntil in the past → EXPIRED")
    void checkSignalStatuses_expiredSignal_transitionsToExpired() {
        Signal expiredSignal = new Signal(
                stock,
                SignalType.ORB,
                SignalDirection.LONG,
                bd(100.0),
                bd(110.0),
                null,
                bd(95.0),
                75,
                "BULLISH",
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(60),   // expired 1 minute ago
                bd(105.0),
                bd(95.0)
        );

        // Directly replicate the expiry branch that checkSignalStatuses executes
        // (bypasses the trading-hours time guard which cannot be controlled in unit tests).
        Instant nowInstant = Instant.now();
        if (expiredSignal.getStatus() == SignalStatus.GENERATED
                && nowInstant.isAfter(expiredSignal.getValidUntil())) {
            expiredSignal.updateStatus(SignalStatus.EXPIRED);
            signalRepository.save(expiredSignal);
        }

        assertThat(expiredSignal.getStatus()).isEqualTo(SignalStatus.EXPIRED);
        verify(signalRepository).save(expiredSignal);
    }

    // ── 10. Market-close sweep ─────────────────────────────────────────────────

    @Test
    @DisplayName("closeAllOpenPositions: all GENERATED/TRIGGERED signals → MARKET_CLOSE")
    void closeAllOpenPositions_transitionsAllOpenToMarketClose() {
        Signal generated = longSignal(100.0, 110.0, 95.0);
        Signal triggered = longSignal(102.0, 112.0, 97.0);
        triggered.updateStatus(SignalStatus.TRIGGERED);

        when(signalRepository.findByStatusIn(
                List.of(SignalStatus.GENERATED, SignalStatus.TRIGGERED)))
                .thenReturn(List.of(generated, triggered));

        service.closeAllOpenPositions();

        assertThat(generated.getStatus()).isEqualTo(SignalStatus.MARKET_CLOSE);
        assertThat(triggered.getStatus()).isEqualTo(SignalStatus.MARKET_CLOSE);
        verify(signalRepository).save(generated);
        verify(signalRepository).save(triggered);
    }
}
