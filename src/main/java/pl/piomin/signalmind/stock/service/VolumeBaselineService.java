package pl.piomin.signalmind.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.signalmind.market.service.TradingCalendarService;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.domain.VolumeBaseline;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;
import pl.piomin.signalmind.stock.repository.VolumeBaselineRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Calculates and persists 20-trading-day time-slot volume baselines
 * for each active stock.
 *
 * <p>Algorithm per stock:
 * <ol>
 *   <li>Resolve the last {@value #WINDOW_DAYS} IST trading days (excluding today).</li>
 *   <li>Load all candles for those days from the database.</li>
 *   <li>Group by IST minute slot and compute the mean volume.</li>
 *   <li>Replace the existing baselines with the newly computed set inside
 *       a single transaction (delete-then-insert).</li>
 * </ol>
 */
@Service
public class VolumeBaselineService {

    private static final Logger log = LoggerFactory.getLogger(VolumeBaselineService.class);

    /** Indian Standard Time zone used for all slot calculations. */
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Number of historical trading days used to compute each baseline. */
    public static final int WINDOW_DAYS = 20;

    private final VolumeBaselineRepository baselineRepository;
    private final CandleRepository candleRepository;
    private final StockRepository stockRepository;
    private final TradingCalendarService tradingCalendar;

    public VolumeBaselineService(VolumeBaselineRepository baselineRepository,
                                 CandleRepository candleRepository,
                                 StockRepository stockRepository,
                                 TradingCalendarService tradingCalendar) {
        this.baselineRepository = baselineRepository;
        this.candleRepository = candleRepository;
        this.stockRepository = stockRepository;
        this.tradingCalendar = tradingCalendar;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Recalculates and persists baselines for every active stock.
     * Logs symbol and elapsed time at INFO level for each stock.
     */
    public void recalculateAll() {
        List<Stock> stocks = stockRepository.findByActiveTrueOrderBySymbolAsc();
        log.info("[baseline] Starting recalculation for {} active stocks", stocks.size());
        for (Stock stock : stocks) {
            long start = System.currentTimeMillis();
            recalculate(stock);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[baseline] {} — recalculated in {}ms", stock.getSymbol(), elapsed);
        }
    }

    /**
     * Recalculates and persists baselines for a single stock.
     *
     * <p>All database writes (delete + insert) are executed within a single
     * transaction so the table is never left in a partially-updated state.
     *
     * @param stock the stock to process
     */
    @Transactional
    public void recalculate(Stock stock) {
        List<LocalDate> days = lastNTradingDays(WINDOW_DAYS);
        if (days.isEmpty()) {
            log.warn("[baseline] {} — no trading days found, skipping", stock.getSymbol());
            return;
        }

        // days is ordered newest → oldest; oldest is last in the list.
        Instant from = days.get(days.size() - 1).atStartOfDay(IST).toInstant();
        // Upper bound: end of newest day's session (15:30 IST, exclusive upper bound).
        Instant to = days.get(0).atTime(15, 30).atZone(IST).toInstant();

        List<Candle> candles = candleRepository.findByStockAndTimeRange(stock.getId(), from, to);

        // Group by IST minute slot and accumulate volume statistics.
        Map<LocalTime, LongSummaryStatistics> stats = candles.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCandleTime().atZone(IST).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                        Collectors.summarizingLong(Candle::getVolume)));

        Instant computedAt = Instant.now();
        List<VolumeBaseline> baselines = new ArrayList<>(stats.size());
        for (Map.Entry<LocalTime, LongSummaryStatistics> entry : stats.entrySet()) {
            LongSummaryStatistics st = entry.getValue();
            long avgVolume = Math.round(st.getAverage());
            int sampleDays = (int) st.getCount();
            baselines.add(new VolumeBaseline(stock, entry.getKey(), avgVolume, sampleDays, computedAt));
        }

        baselineRepository.deleteAllByStock(stock);
        baselineRepository.saveAll(baselines);

        log.debug("[baseline] {} — {} slots computed from {} candles over {} days",
                stock.getSymbol(), baselines.size(), candles.size(), days.size());
    }

    /**
     * Returns all baselines for the given symbol, ordered by slot time ascending.
     *
     * @param symbol NSE stock symbol
     * @return list of baselines (empty if none have been computed yet)
     * @throws NoSuchElementException if the symbol is not registered
     */
    public List<VolumeBaseline> findBaselines(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("Unknown symbol: " + symbol));
        return baselineRepository.findByStockOrderBySlotTimeAsc(stock);
    }

    /**
     * Returns the baseline for a specific minute slot.
     *
     * @param symbol   NSE stock symbol
     * @param slotTime minute slot in IST (e.g. {@code LocalTime.of(9, 30)})
     * @return the matching baseline, or empty if none has been computed for that slot
     * @throws NoSuchElementException if the symbol is not registered
     */
    public Optional<VolumeBaseline> findBaseline(String symbol, LocalTime slotTime) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("Unknown symbol: " + symbol));
        return baselineRepository.findByStockAndSlotTime(stock, slotTime);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Returns the last {@code n} IST trading days, starting from yesterday,
     * ordered newest to oldest.
     *
     * <p>Walks backwards from {@code yesterday} until {@code n} valid trading
     * days have been collected. Saturdays, Sundays, and declared NSE holidays
     * are skipped.
     *
     * @param n number of trading days to collect
     * @return list of trading days, newest first; may be shorter than {@code n}
     *         if the walk-back limit (5 × n calendar days) is reached first
     */
    List<LocalDate> lastNTradingDays(int n) {
        List<LocalDate> result = new ArrayList<>(n);
        LocalDate cursor = LocalDate.now(IST).minusDays(1); // start from yesterday
        // Safety cap: never scan more than 5× calendar days to find n trading days.
        int maxLookback = n * 5;
        int scanned = 0;
        while (result.size() < n && scanned < maxLookback) {
            if (tradingCalendar.isTradingDay(cursor)) {
                result.add(cursor);
            }
            cursor = cursor.minusDays(1);
            scanned++;
        }
        return result;
    }
}
