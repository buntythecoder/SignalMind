package pl.piomin.signalmind.signal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalStatus;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.signal.sse.SignalSseService;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates open signal statuses every minute during the trading session (SM-31).
 *
 * <h2>Scheduling</h2>
 * <ul>
 *   <li>{@link #checkSignalStatuses()} runs at second=35 of every minute (Mon-Fri, IST)
 *       giving the candle assembler a 5-second head start to close the previous minute.</li>
 *   <li>{@link #closeAllOpenPositions()} runs at exactly 15:30 IST to sweep any remaining
 *       GENERATED or TRIGGERED signals to MARKET_CLOSE — no overnight carry.</li>
 * </ul>
 *
 * <h2>Entry zone logic</h2>
 * A signal transitions from GENERATED to TRIGGERED when:
 * <ul>
 *   <li>LONG: the candle's {@code high >= entryPrice}</li>
 *   <li>SHORT: the candle's {@code low <= entryPrice}</li>
 * </ul>
 *
 * <h2>SSE broadcasting</h2>
 * Every status transition is broadcast via {@link SignalSseService} when present.
 * The SSE service is optional — if the spring context does not have one (e.g. tests),
 * the service degrades gracefully.
 */
@Service
public class SignalStatusService {

    private static final Logger log = LoggerFactory.getLogger(SignalStatusService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime ENGINE_START = LocalTime.of(9, 15);
    private static final LocalTime ENGINE_END   = LocalTime.of(15, 30);

    /** Entry-zone tolerance: 0.2% of entry price (bidirectional). */
    private static final BigDecimal ENTRY_ZONE_PCT = new BigDecimal("0.002");

    private final SignalRepository signalRepository;
    private final CandleRepository candleRepository;
    private final Optional<SignalSseService> sseService;

    public SignalStatusService(SignalRepository signalRepository,
                               CandleRepository candleRepository,
                               Optional<SignalSseService> sseService) {
        this.signalRepository = signalRepository;
        this.candleRepository = candleRepository;
        this.sseService        = sseService;
    }

    // ── Scheduled tasks ───────────────────────────────────────────────────────

    /**
     * Called every minute at second 35 (after the candle assembler closes the minute).
     * Loads all GENERATED/TRIGGERED signals and evaluates price transitions.
     * Returns immediately outside the [09:15, 15:30] IST window.
     */
    @Scheduled(cron = "35 * * * * MON-FRI", zone = "Asia/Kolkata")
    public void checkSignalStatuses() {
        LocalTime now = LocalTime.now(IST);
        if (now.isBefore(ENGINE_START) || now.isAfter(ENGINE_END)) {
            return;
        }

        LocalDate today        = LocalDate.now(IST);
        Instant sessionStart   = today.atTime(9, 15).atZone(IST).toInstant();
        Instant nowInstant     = Instant.now();

        List<Signal> activeSignals = signalRepository.findByStatusIn(
                List.of(SignalStatus.GENERATED, SignalStatus.TRIGGERED));

        for (Signal signal : activeSignals) {

            // Check expiry first — only GENERATED signals can expire (TRIGGERED are live)
            if (signal.getStatus() == SignalStatus.GENERATED
                    && nowInstant.isAfter(signal.getValidUntil())) {
                transition(signal, SignalStatus.EXPIRED);
                continue;
            }

            // Get the most recent candle for this stock within today's session
            List<Candle> recentCandles = candleRepository.findByStockAndTimeRange(
                    signal.getStock().getId(), sessionStart, nowInstant);
            if (recentCandles.isEmpty()) {
                continue;
            }

            // findByStockAndTimeRange returns newest-first; index 0 is the latest candle
            Candle latest = recentCandles.get(0);
            evaluateSignal(signal, latest.getHigh(), latest.getLow());
        }
    }

    /**
     * At 3:30 PM, sweeps all remaining GENERATED/TRIGGERED signals to MARKET_CLOSE.
     * No intraday positions carry overnight.
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void closeAllOpenPositions() {
        List<Signal> openSignals = signalRepository.findByStatusIn(
                List.of(SignalStatus.GENERATED, SignalStatus.TRIGGERED));
        for (Signal signal : openSignals) {
            transition(signal, SignalStatus.MARKET_CLOSE);
        }
        log.info("[signal-status] Market close: swept {} open signals to MARKET_CLOSE",
                openSignals.size());
    }

    // ── Core evaluation logic (package-private for unit testing) ─────────────

    /**
     * Evaluates a single signal against the latest candle's high/low prices and
     * transitions it to the appropriate terminal or intermediate status.
     *
     * <p>For a GENERATED signal: checks if price has entered the entry zone.
     * For a TRIGGERED signal: checks stop-loss, T2, and T1 in priority order.
     *
     * @param signal the signal to evaluate (mutated in place on transition)
     * @param high   the candle's high price
     * @param low    the candle's low price
     */
    void evaluateSignal(Signal signal, BigDecimal high, BigDecimal low) {
        boolean isLong = signal.getDirection() == SignalDirection.LONG;

        if (signal.getStatus() == SignalStatus.GENERATED) {
            boolean triggered = isLong
                    ? high.compareTo(signal.getEntryPrice()) >= 0
                    : low.compareTo(signal.getEntryPrice()) <= 0;

            if (!triggered) {
                return;
            }
            transition(signal, SignalStatus.TRIGGERED);
            // Fall through: check if stop/target also hit in the same candle
        }

        if (signal.getStatus() == SignalStatus.TRIGGERED) {
            if (isLong) {
                // Stop takes priority over targets
                if (low.compareTo(signal.getStopLoss()) <= 0) {
                    transition(signal, SignalStatus.STOP_HIT);
                    return;
                }
                // T2 before T1 (wider target)
                if (signal.getTarget2() != null
                        && high.compareTo(signal.getTarget2()) >= 0) {
                    transition(signal, SignalStatus.TARGET_2_HIT);
                    return;
                }
                if (high.compareTo(signal.getTargetPrice()) >= 0) {
                    transition(signal, SignalStatus.TARGET_1_HIT);
                }
            } else {
                // SHORT
                if (high.compareTo(signal.getStopLoss()) >= 0) {
                    transition(signal, SignalStatus.STOP_HIT);
                    return;
                }
                if (signal.getTarget2() != null
                        && low.compareTo(signal.getTarget2()) <= 0) {
                    transition(signal, SignalStatus.TARGET_2_HIT);
                    return;
                }
                if (low.compareTo(signal.getTargetPrice()) <= 0) {
                    transition(signal, SignalStatus.TARGET_1_HIT);
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Persists the status transition and broadcasts it via SSE.
     *
     * @param signal    the signal to update
     * @param newStatus the target status
     */
    private void transition(Signal signal, SignalStatus newStatus) {
        SignalStatus previousStatus = signal.getStatus();
        signal.updateStatus(newStatus);
        signalRepository.save(signal);
        sseService.ifPresent(sse -> sse.broadcastStatusChange(signal.getId(), newStatus.name()));
        log.info("[signal-status] signal={} stock={} {} → {}",
                signal.getId(), signal.getStock().getSymbol(),
                previousStatus, newStatus);
    }
}
