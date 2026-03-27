package pl.piomin.signalmind.candle.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPI for a rolling price-close buffer used to feed {@link RsiCalculator}.
 *
 * <p>Implementations are free to choose any backing store (in-process list,
 * Redis list, etc.). The buffer holds at most 60 close prices per symbol —
 * enough history to seed Wilder's 14-period RSI plus a margin of several candles.
 *
 * <p>SM-19
 */
public interface CandleRollingBuffer {

    /**
     * Push the latest 1-minute close price for {@code symbol} into the buffer.
     * The implementation must ensure the total stored size does not exceed 60
     * entries (discard the oldest entry when the limit is reached).
     *
     * @param symbol NSE trading symbol
     * @param close  candle close price
     */
    void pushClose(String symbol, BigDecimal close);

    /**
     * Retrieve up to {@code count} recent close prices for {@code symbol},
     * returned in <em>oldest-first</em> order so they can be passed directly
     * to {@link RsiCalculator#compute(List)}.
     *
     * @param symbol NSE trading symbol
     * @param count  maximum number of closes to return; must be ≤ 60
     * @return immutable list of close prices, oldest-first; may be shorter than {@code count}
     *         if insufficient data is available
     */
    List<BigDecimal> getCloses(String symbol, int count);

    /**
     * A human-readable name for this implementation, used in logging and
     * Spring actuator info output.
     */
    String bufferName();
}
