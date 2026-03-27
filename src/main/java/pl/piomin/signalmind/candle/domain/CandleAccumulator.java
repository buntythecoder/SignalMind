package pl.piomin.signalmind.candle.domain;

import pl.piomin.signalmind.ingestion.domain.MarketTick;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thread-safe in-memory accumulator for a single 1-minute candle slot.
 *
 * <p>One {@code CandleAccumulator} is created per (symbol, minute-slot) pair by
 * {@link pl.piomin.signalmind.candle.service.MinuteCandleAssembler}. All public
 * mutation methods are {@code synchronized} so that ticks arriving concurrently
 * from multiple ingestion threads do not produce a data race on OHLCV state.
 *
 * <p>SM-19
 */
public class CandleAccumulator {

    private final String symbol;
    private final Instant slotStart;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private long volume;
    private int tickCount;

    public CandleAccumulator(String symbol, Instant slotStart) {
        this.symbol = symbol;
        this.slotStart = slotStart;
    }

    /**
     * Incorporate one market tick into the running OHLCV state.
     *
     * <p>On the first tick {@code ltp} becomes {@code open}. On every subsequent
     * tick {@code high}/{@code low} are updated and {@code close} is always
     * overwritten with the latest price. Volume is accumulated additively.
     */
    public synchronized void processTick(MarketTick tick) {
        BigDecimal price = tick.ltp();
        long vol = tick.volume();

        if (tickCount == 0) {
            open = price;
            high = price;
            low = price;
        } else {
            if (price.compareTo(high) > 0) {
                high = price;
            }
            if (price.compareTo(low) < 0) {
                low = price;
            }
        }
        close = price;
        volume += vol;
        tickCount++;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String symbol() {
        return symbol;
    }

    public Instant slotStart() {
        return slotStart;
    }

    public synchronized BigDecimal open() {
        return open;
    }

    public synchronized BigDecimal high() {
        return high;
    }

    public synchronized BigDecimal low() {
        return low;
    }

    public synchronized BigDecimal close() {
        return close;
    }

    public synchronized long volume() {
        return volume;
    }

    public synchronized int tickCount() {
        return tickCount;
    }

    /** Returns {@code true} when no ticks have been processed yet. */
    public synchronized boolean isEmpty() {
        return tickCount == 0;
    }
}
