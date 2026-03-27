package pl.piomin.signalmind.signal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalOutcome;
import pl.piomin.signalmind.signal.domain.SignalStatus;
import pl.piomin.signalmind.signal.repository.SignalOutcomeRepository;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Records the final outcome of every signal that reached a terminal state during
 * the trading session (SM-32).
 *
 * <h2>Scheduling</h2>
 * Runs once at 15:35 IST on weekdays — five minutes after market close — so that
 * all intraday status transitions have been flushed by {@link SignalStatusService}.
 *
 * <h2>Outcome mapping</h2>
 * <ul>
 *   <li>TARGET_1_HIT / TARGET_2_HIT → outcome {@code TARGET_1_HIT} / {@code TARGET_2_HIT},
 *       exit price = respective target, pnl = target − entry (LONG) or entry − target (SHORT)</li>
 *   <li>STOP_HIT → outcome {@code STOP_HIT}, exit price = stopLoss, pnl is negative for the
 *       relevant direction</li>
 *   <li>EXPIRED / MARKET_CLOSE → outcome {@code EXPIRED}, exit price = latest candle close,
 *       pnl reflects actual close vs entry</li>
 * </ul>
 *
 * <h2>Redis win-rate cache</h2>
 * After recording outcomes the service increments counters in Redis so the
 * Telegram summary can read them without a DB query:
 * <pre>
 *   winrate:{signalType}:wins
 *   winrate:{signalType}:losses
 *   winrate:{signalType}:expires
 * </pre>
 * Redis is optional — the service degrades gracefully when no {@link StringRedisTemplate}
 * is present in the context.
 */
@Service
public class OutcomeTrackerService {

    private static final Logger log = LoggerFactory.getLogger(OutcomeTrackerService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Terminal statuses that require an outcome row. */
    private static final Set<SignalStatus> TERMINAL_STATUSES = EnumSet.of(
            SignalStatus.TARGET_1_HIT,
            SignalStatus.TARGET_2_HIT,
            SignalStatus.STOP_HIT,
            SignalStatus.EXPIRED,
            SignalStatus.MARKET_CLOSE
    );

    /** Redis key TTL — 30 days so win-rate history survives across weekends. */
    private static final Duration REDIS_TTL = Duration.ofDays(30);

    private final SignalRepository        signalRepository;
    private final SignalOutcomeRepository outcomeRepository;
    private final CandleRepository        candleRepository;
    private final Optional<StringRedisTemplate> redisTemplate;

    public OutcomeTrackerService(SignalRepository signalRepository,
                                 SignalOutcomeRepository outcomeRepository,
                                 CandleRepository candleRepository,
                                 Optional<StringRedisTemplate> redisTemplate) {
        this.signalRepository  = signalRepository;
        this.outcomeRepository = outcomeRepository;
        this.candleRepository  = candleRepository;
        this.redisTemplate     = redisTemplate;
    }

    // ── Scheduled task ────────────────────────────────────────────────────────

    /**
     * Runs at 15:35 IST on trading days.  Iterates every signal in a terminal state
     * that does not yet have an outcome record and persists one.  After all records
     * are saved the Redis win-rate counters are updated atomically.
     */
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Kolkata")
    @Transactional
    public void recordDailyOutcomes() {
        LocalDate today        = LocalDate.now(IST);
        Instant   sessionStart = today.atTime(LocalTime.of(9, 15)).atZone(IST).toInstant();
        Instant   sessionEnd   = today.atTime(LocalTime.of(15, 35)).atZone(IST).toInstant();

        List<Signal> terminalSignals = signalRepository.findByStatusIn(TERMINAL_STATUSES);

        int recorded = 0;
        for (Signal signal : terminalSignals) {
            // Only process signals generated today
            if (signal.getGeneratedAt().isBefore(sessionStart)
                    || signal.getGeneratedAt().isAfter(sessionEnd)) {
                continue;
            }

            if (outcomeRepository.existsBySignalId(signal.getId())) {
                log.debug("[outcome-tracker] Outcome already exists for signal={}", signal.getId());
                continue;
            }

            BigDecimal exitPrice = resolveExitPrice(signal, sessionStart, sessionEnd);
            SignalOutcome outcome = buildOutcome(signal, exitPrice);
            outcomeRepository.save(outcome);
            updateRedisCounters(signal, outcome.getOutcome());
            recorded++;
        }

        log.info("[outcome-tracker] Recorded {} outcomes for session {}", recorded, today);
    }

    // ── Public query API ──────────────────────────────────────────────────────

    /**
     * Returns a win-rate summary for the outcomes recorded within the given window.
     * Reads from the {@code signal_outcomes} table — not from Redis — so it is always
     * consistent even when Redis is absent.
     *
     * @param sessionStart inclusive start of the session window
     * @param sessionEnd   inclusive end of the session window
     * @return aggregated win/loss/expire counts for the day
     */
    public WinRateSummary getDailySummary(Instant sessionStart, Instant sessionEnd) {
        List<SignalOutcome> outcomes = outcomeRepository.findByRecordedAtBetween(sessionStart, sessionEnd);

        long wins    = outcomes.stream().filter(o -> isWin(o.getOutcome())).count();
        long losses  = outcomes.stream().filter(o -> "STOP_HIT".equals(o.getOutcome())).count();
        long expires = outcomes.stream().filter(o -> "EXPIRED".equals(o.getOutcome())).count();
        long total   = outcomes.size();

        return new WinRateSummary(total, wins, losses, expires);
    }

    // ── Package-private for unit testing ─────────────────────────────────────

    /**
     * Records an outcome for the given signal using the pre-resolved exit price.
     * Skips the call if an outcome row already exists (idempotent).
     *
     * @param signal    the signal to record an outcome for
     * @param exitPrice the price at which the signal was resolved
     */
    void recordOutcome(Signal signal, BigDecimal exitPrice) {
        if (outcomeRepository.existsBySignalId(signal.getId())) {
            log.debug("[outcome-tracker] Skipping already-recorded signal={}", signal.getId());
            return;
        }
        SignalOutcome outcome = buildOutcome(signal, exitPrice);
        outcomeRepository.save(outcome);
        updateRedisCounters(signal, outcome.getOutcome());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Determines the exit price for a signal based on its terminal status.
     * <ul>
     *   <li>TARGET_1_HIT → targetPrice</li>
     *   <li>TARGET_2_HIT → target2 (falls back to targetPrice if null)</li>
     *   <li>STOP_HIT     → stopLoss</li>
     *   <li>EXPIRED / MARKET_CLOSE → latest candle close in the session window</li>
     * </ul>
     */
    private BigDecimal resolveExitPrice(Signal signal, Instant from, Instant to) {
        return switch (signal.getStatus()) {
            case TARGET_1_HIT -> signal.getTargetPrice();
            case TARGET_2_HIT -> signal.getTarget2() != null
                    ? signal.getTarget2()
                    : signal.getTargetPrice();
            case STOP_HIT -> signal.getStopLoss();
            default -> {
                // EXPIRED or MARKET_CLOSE — use latest candle close
                List<Candle> candles = candleRepository.findByStockAndTimeRange(
                        signal.getStock().getId(), from, to);
                yield candles.isEmpty()
                        ? signal.getEntryPrice()   // fallback: treat as flat
                        : candles.get(0).getClose(); // newest-first
            }
        };
    }

    /**
     * Constructs a {@link SignalOutcome} from the signal and its resolved exit price.
     * Calculates P&L points respecting direction (LONG profits when price rises, SHORT when it falls).
     */
    private SignalOutcome buildOutcome(Signal signal, BigDecimal exitPrice) {
        String outcomeCode = toOutcomeCode(signal.getStatus());
        BigDecimal pnl     = calculatePnl(signal, exitPrice);
        return new SignalOutcome(signal.getId(), outcomeCode, exitPrice, Instant.now(), pnl);
    }

    /**
     * Maps a terminal {@link SignalStatus} to the outcome code stored in the DB.
     * MARKET_CLOSE is treated as EXPIRED for the outcome table's CHECK constraint.
     */
    private static String toOutcomeCode(SignalStatus status) {
        return switch (status) {
            case TARGET_1_HIT -> "TARGET_1_HIT";
            case TARGET_2_HIT -> "TARGET_2_HIT";
            case STOP_HIT     -> "STOP_HIT";
            default           -> "EXPIRED";   // EXPIRED or MARKET_CLOSE
        };
    }

    /**
     * Calculates P&L in price points.
     * LONG:  exitPrice − entryPrice  (positive = profit)
     * SHORT: entryPrice − exitPrice  (positive = profit)
     */
    private static BigDecimal calculatePnl(Signal signal, BigDecimal exitPrice) {
        if (exitPrice == null) {
            return BigDecimal.ZERO;
        }
        return signal.getDirection() == SignalDirection.LONG
                ? exitPrice.subtract(signal.getEntryPrice())
                : signal.getEntryPrice().subtract(exitPrice);
    }

    /** Returns {@code true} for win outcomes (target reached). */
    private static boolean isWin(String outcomeCode) {
        return "TARGET_1_HIT".equals(outcomeCode) || "TARGET_2_HIT".equals(outcomeCode);
    }

    /**
     * Atomically increments the appropriate Redis win-rate counter for the signal type.
     * Sets a 30-day TTL on the first write.  No-op when Redis is absent.
     */
    private void updateRedisCounters(Signal signal, String outcomeCode) {
        if (redisTemplate.isEmpty()) {
            return;
        }
        StringRedisTemplate redis = redisTemplate.get();
        String signalType = signal.getSignalType().name();

        String counterKey;
        if (isWin(outcomeCode)) {
            counterKey = "winrate:" + signalType + ":wins";
        } else if ("STOP_HIT".equals(outcomeCode)) {
            counterKey = "winrate:" + signalType + ":losses";
        } else {
            counterKey = "winrate:" + signalType + ":expires";
        }

        Boolean isNew = redis.opsForValue().setIfAbsent(counterKey, "0");
        if (Boolean.TRUE.equals(isNew)) {
            redis.expire(counterKey, REDIS_TTL);
        }
        redis.opsForValue().increment(counterKey);
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    /**
     * Immutable summary of a trading session's signal outcomes.
     *
     * @param total   total number of signals with recorded outcomes
     * @param wins    number of TARGET_1_HIT or TARGET_2_HIT outcomes
     * @param losses  number of STOP_HIT outcomes
     * @param expires number of EXPIRED (including MARKET_CLOSE mapped to EXPIRED) outcomes
     */
    public record WinRateSummary(long total, long wins, long losses, long expires) {

        /**
         * Returns the win rate as a formatted percentage string (e.g. {@code "60%"}).
         * Returns {@code "N/A"} when there are no outcomes to report.
         */
        public String formattedWinRate() {
            if (total == 0) {
                return "N/A";
            }
            return String.format("%.0f%%", (wins * 100.0 / total));
        }
    }
}
