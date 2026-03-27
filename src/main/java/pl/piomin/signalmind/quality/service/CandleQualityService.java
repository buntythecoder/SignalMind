package pl.piomin.signalmind.quality.service;

import org.springframework.stereotype.Service;
import pl.piomin.signalmind.market.service.TradingCalendarService;
import pl.piomin.signalmind.quality.domain.CandleIssue;
import pl.piomin.signalmind.quality.domain.DayQualityReport;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Orchestrates all registered {@link DataQualityValidator} implementations
 * to produce {@link DayQualityReport}s for a given stock and date (range).
 *
 * <p>Validators are discovered automatically via Spring's
 * {@code List<DataQualityValidator>} injection — the same pattern used by
 * {@code CandleIngestionService} for {@code HistoricalDataProvider}.
 */
@Service
public class CandleQualityService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_RANGE_DAYS = 31;

    private final List<DataQualityValidator> validators;
    private final CandleRepository candleRepository;
    private final StockRepository stockRepository;
    private final TradingCalendarService tradingCalendar;

    public CandleQualityService(List<DataQualityValidator> validators,
                                CandleRepository candleRepository,
                                StockRepository stockRepository,
                                TradingCalendarService tradingCalendar) {
        this.validators = validators;
        this.candleRepository = candleRepository;
        this.stockRepository = stockRepository;
        this.tradingCalendar = tradingCalendar;
    }

    /**
     * Validates candle data for a single stock on a single trading day.
     *
     * @param symbol     NSE stock symbol
     * @param tradingDay the calendar date (IST) to validate
     * @return quality report containing all issues found
     * @throws NoSuchElementException if {@code symbol} is not registered in the database
     */
    public DayQualityReport validateDay(String symbol, LocalDate tradingDay) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("Unknown symbol: " + symbol));

        // Derive UTC instant window for the IST trading day.
        Instant from = tradingDay.atStartOfDay(IST).toInstant();
        Instant to = tradingDay.plusDays(1).atStartOfDay(IST).toInstant();

        List<Candle> candles = candleRepository.findByStockAndTimeRange(stock.getId(), from, to);

        // Run all validators that support this symbol and aggregate issues.
        List<CandleIssue> allIssues = new ArrayList<>();
        for (DataQualityValidator validator : validators) {
            if (validator.supports(symbol)) {
                allIssues.addAll(validator.validate(symbol, tradingDay, candles));
            }
        }

        int expectedCandles = tradingCalendar.expectedCandleCount(tradingDay);
        return new DayQualityReport(symbol, tradingDay, candles.size(), expectedCandles, allIssues);
    }

    /**
     * Validates every day in the inclusive range [{@code from}, {@code to}].
     *
     * @param symbol NSE stock symbol
     * @param from   start date (IST), inclusive
     * @param to     end date (IST), inclusive
     * @return one report per calendar day in the range
     * @throws IllegalArgumentException if the range exceeds 31 days
     * @throws NoSuchElementException   if {@code symbol} is not registered
     */
    public List<DayQualityReport> validateRange(String symbol, LocalDate from, LocalDate to) {
        long days = from.until(to, java.time.temporal.ChronoUnit.DAYS) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Date range must not exceed " + MAX_RANGE_DAYS + " days, but got " + days);
        }

        List<DayQualityReport> reports = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            reports.add(validateDay(symbol, cursor));
            cursor = cursor.plusDays(1);
        }
        return reports;
    }

    /**
     * Returns the names of all registered validators, in discovery order.
     */
    public List<String> availableValidators() {
        return validators.stream()
                .map(DataQualityValidator::validatorName)
                .toList();
    }
}
