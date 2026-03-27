package pl.piomin.signalmind.quality.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated data-quality summary for one stock on one trading day.
 *
 * @param symbol           NSE stock symbol
 * @param tradingDay       the calendar date that was validated
 * @param totalCandlesFound number of candles actually present in the database
 * @param expectedCandles  number of candles expected (375 for a trading day, 0 otherwise)
 * @param issues           all quality issues detected across all validators
 */
public record DayQualityReport(
        String symbol,
        LocalDate tradingDay,
        int totalCandlesFound,
        int expectedCandles,
        List<CandleIssue> issues
) {

    /**
     * Returns {@code true} when no issues were found and the candle count matches expectations.
     */
    public boolean isValid() {
        return issues.isEmpty() && totalCandlesFound == expectedCandles;
    }

    /**
     * Returns the number of {@link QualityIssue#MISSING_CANDLE} issues in this report.
     */
    public long missingCandleCount() {
        return issues.stream()
                .filter(i -> i.issueType() == QualityIssue.MISSING_CANDLE)
                .count();
    }

    /**
     * Returns the count of issues that are NOT {@link QualityIssue#MISSING_CANDLE}
     * (i.e. OHLCV integrity or session-boundary problems).
     */
    public long ohlcvIssueCount() {
        return issues.stream()
                .filter(i -> i.issueType() != QualityIssue.MISSING_CANDLE)
                .count();
    }
}
