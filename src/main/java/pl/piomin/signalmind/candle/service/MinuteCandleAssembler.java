package pl.piomin.signalmind.candle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.piomin.signalmind.candle.domain.CandleAccumulator;
import pl.piomin.signalmind.candle.domain.VwapState;
import pl.piomin.signalmind.ingestion.domain.MarketTick;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Assembles raw market ticks into 1-minute OHLCV candles enriched with
 * cumulative VWAP and rolling RSI(14).
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>One {@link CandleAccumulator} per (symbol, minute-slot) absorbs incoming ticks.</li>
 *   <li>{@link CandleFlushScheduler} calls {@link #flushMinute(Instant)} at second :02
 *       of each minute, providing a 2-second late-tick buffer.</li>
 *   <li>{@link VwapState} is kept in a {@code ConcurrentHashMap} and resets at session
 *       open (09:15 IST) via {@link #resetDailyVwapState()}.</li>
 *   <li>RSI is computed from the last 15 closes provided by the injected
 *       {@link CandleRollingBuffer}. When no buffer is available (e.g. Redis absent)
 *       RSI is stored as {@code null}.</li>
 * </ul>
 *
 * <p>Only activated when {@code angelone.ingestion.enabled=true}; absent in all
 * non-live profiles so no test mocking is required.
 *
 * <p>SM-19
 */
@Component
@ConditionalOnProperty(name = "angelone.ingestion.enabled", matchIfMissing = false)
public class MinuteCandleAssembler {

    private static final Logger log = LoggerFactory.getLogger(MinuteCandleAssembler.class);

    @SuppressWarnings("unused")
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // symbol → accumulator for the current IST minute slot
    private final ConcurrentHashMap<String, CandleAccumulator> accumulators =
            new ConcurrentHashMap<>();

    // symbol → per-session VWAP state (reset at 09:15 IST each trading day)
    private final ConcurrentHashMap<String, VwapState> vwapStates =
            new ConcurrentHashMap<>();

    private final CandleRepository candleRepository;
    private final StockRepository stockRepository;
    private final Optional<CandleRollingBuffer> rollingBuffer;

    // Virtual-thread executor for fire-and-forget Redis operations
    private final Executor asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public MinuteCandleAssembler(CandleRepository candleRepository,
                                 StockRepository stockRepository,
                                 Optional<CandleRollingBuffer> rollingBuffer) {
        this.candleRepository = candleRepository;
        this.stockRepository  = stockRepository;
        this.rollingBuffer    = rollingBuffer;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Feed a single market tick into the current-minute accumulator for its symbol.
     *
     * <p>If the tick belongs to a new minute slot a fresh {@link CandleAccumulator}
     * is created, replacing the stale one (the stale candle will be flushed by the
     * scheduler at second :02 of the current minute, i.e. shortly after this tick).
     */
    public void processTick(MarketTick tick) {
        String symbol    = tick.symbol();
        Instant slotStart = currentMinuteSlot(tick.receivedAt());

        accumulators.compute(symbol, (sym, existing) -> {
            if (existing == null || !existing.slotStart().equals(slotStart)) {
                return new CandleAccumulator(sym, slotStart);
            }
            return existing;
        }).processTick(tick);
    }

    /**
     * Flush the accumulated candle for the given minute slot.
     *
     * <p>Called by {@link CandleFlushScheduler} at second :02 of every minute.
     * Iterates over all in-flight accumulators, extracts those whose
     * {@code slotStart} matches {@code minuteSlot}, removes them from the map,
     * and persists the resulting candle with indicators.
     *
     * @param minuteSlot the exact minute-boundary {@link Instant} to flush
     *                   (e.g. {@code 2024-01-15T09:16:00Z})
     */
    public void flushMinute(Instant minuteSlot) {
        accumulators.forEach((symbol, acc) -> {
            if (acc.slotStart().equals(minuteSlot) && !acc.isEmpty()) {
                // Atomic remove: only this thread should process acc if it wins
                if (accumulators.remove(symbol, acc)) {
                    persistCandle(symbol, acc);
                }
            }
        });
    }

    /**
     * Clear all per-stock VWAP state at session open (09:15 IST).
     * Called by {@link CandleFlushScheduler#resetDailyState()}.
     */
    public void resetDailyVwapState() {
        log.info("[candle] Resetting daily VWAP state for new session");
        vwapStates.clear();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void persistCandle(String symbol, CandleAccumulator acc) {
        try {
            Stock stock = stockRepository.findBySymbol(symbol)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unknown symbol in candle assembler: " + symbol));

            // VWAP — update cumulative state for the session
            VwapState vwapState = vwapStates.computeIfAbsent(symbol, s -> new VwapState());
            vwapState.update(acc.high(), acc.low(), acc.close(), acc.volume());

            BigDecimal vwap      = vwapState.currentVwap();
            BigDecimal vwapUpper = vwapState.upperBand();
            BigDecimal vwapLower = vwapState.lowerBand();

            // RSI — push close to rolling buffer, then compute
            BigDecimal rsi = null;
            if (rollingBuffer.isPresent()) {
                CandleRollingBuffer buf = rollingBuffer.get();
                buf.pushClose(symbol, acc.close());
                List<BigDecimal> closes = buf.getCloses(symbol, RsiCalculator.PERIOD + 1);
                rsi = RsiCalculator.compute(closes);
            }

            Candle candle = new Candle(
                    stock, acc.slotStart(),
                    acc.open(), acc.high(), acc.low(), acc.close(),
                    acc.volume(), CandleSource.LIVE,
                    vwap, vwapUpper, vwapLower, rsi);

            candleRepository.save(candle);

            // Async hook for future SM-20+ frontend streaming over Redis
            final Candle saved = candle;
            asyncExecutor.execute(() -> updateRedisRollingCandles(symbol, saved));

            log.debug("[candle] Flushed {} @ {} VWAP={} RSI={}",
                    symbol, acc.slotStart(), vwap, rsi);

        } catch (Exception e) {
            log.error("[candle] Failed to persist candle for {} @ {}: {}",
                    symbol, acc.slotStart(), e.getMessage(), e);
        }
    }

    /**
     * Placeholder for SM-20+: push a serialised candle summary to a Redis list
     * keyed by {@code candle:live:{symbol}} for live chart streaming.
     */
    private void updateRedisRollingCandles(String symbol, Candle candle) {
        log.trace("[candle] Async Redis update for {} @ {}", symbol, candle.getCandleTime());
    }

    private Instant currentMinuteSlot(Instant receivedAt) {
        return receivedAt.truncatedTo(ChronoUnit.MINUTES);
    }
}
