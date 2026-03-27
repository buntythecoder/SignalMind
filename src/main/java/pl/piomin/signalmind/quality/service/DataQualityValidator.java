package pl.piomin.signalmind.quality.service;

import pl.piomin.signalmind.quality.domain.CandleIssue;
import pl.piomin.signalmind.stock.domain.Candle;

import java.time.LocalDate;
import java.util.List;

/**
 * SPI for pluggable data-quality validation rules applied to a day's candle set.
 *
 * <p>Implement this interface and annotate the class with {@code @Component} or
 * {@code @Service} to register a new validator. Spring auto-discovers all implementations;
 * {@link CandleQualityService} orchestrates them via {@code List<DataQualityValidator>}
 * injection — the same pattern as {@code HistoricalDataProvider}.
 */
public interface DataQualityValidator {

    /**
     * Short, unique validator identifier used for logging and the
     * {@code GET /api/quality/validators} endpoint.
     * Must be lowercase and hyphenated, e.g. {@code "ohlcv-integrity"}.
     */
    String validatorName();

    /**
     * Inspects {@code candles} and returns every quality issue found.
     *
     * @param symbol     NSE stock symbol being validated
     * @param tradingDay the calendar date (IST) the candles belong to
     * @param candles    candles loaded for this symbol and day (may be empty)
     * @return list of issues; empty list means this validator found nothing wrong
     */
    List<CandleIssue> validate(String symbol, LocalDate tradingDay, List<Candle> candles);

    /**
     * Returns {@code true} if this validator should be applied to {@code symbol}.
     * Default implementation applies to all symbols.
     */
    default boolean supports(String symbol) {
        return true;
    }
}
