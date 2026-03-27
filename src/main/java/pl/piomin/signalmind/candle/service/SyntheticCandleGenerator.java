package pl.piomin.signalmind.candle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.piomin.signalmind.candle.domain.VwapState;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Generates synthetic candles for 1-minute windows where no market ticks arrived (SM-20).
 *
 * <p>A synthetic candle has O=H=L=C=prevClose, Volume=0, and {@code isSynthetic=true}.
 * It is persisted for timeline continuity but must NOT trigger signal detection logic.
 *
 * <p>Returns {@link Optional#empty()} when no prior candle exists for the stock
 * (e.g. the very first minute of trading history), so callers must handle the absent case.
 */
@Component
public class SyntheticCandleGenerator {

    private static final Logger log = LoggerFactory.getLogger(SyntheticCandleGenerator.class);

    private final CandleRepository candleRepository;

    public SyntheticCandleGenerator(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /**
     * Generates and returns a synthetic candle for the given minute slot.
     *
     * <p>Uses the last known close price from the database. Returns empty if no
     * previous candle exists for the stock.
     *
     * @param stock     the stock for which to generate a synthetic candle
     * @param slotStart the minute-start UTC {@link Instant}
     * @param vwapState the current day's VWAP state (may be {@code null} on the first minute)
     * @return an unsaved synthetic {@link Candle}, or empty if no prior candle exists
     */
    public Optional<Candle> generate(Stock stock, Instant slotStart, VwapState vwapState) {
        Optional<Candle> latest = candleRepository.findLatestCandle(stock.getId());
        if (latest.isEmpty()) {
            log.debug("[synthetic] No previous candle for {} — skipping synthetic at {}",
                    stock.getSymbol(), slotStart);
            return Optional.empty();
        }

        BigDecimal prevClose = latest.get().getClose();
        BigDecimal vwap   = vwapState != null ? vwapState.currentVwap() : null;
        BigDecimal upper  = vwapState != null ? vwapState.upperBand()   : null;
        BigDecimal lower  = vwapState != null ? vwapState.lowerBand()   : null;

        Candle synthetic = Candle.synthetic(stock, slotStart, prevClose, vwap, upper, lower);
        log.info("[synthetic] Generated synthetic candle for {} @ {} close={}",
                stock.getSymbol(), slotStart, prevClose);
        return Optional.of(synthetic);
    }
}
