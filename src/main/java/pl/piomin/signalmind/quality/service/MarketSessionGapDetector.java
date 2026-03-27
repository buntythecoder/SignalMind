package pl.piomin.signalmind.quality.service;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.market.service.TradingCalendarService;
import pl.piomin.signalmind.quality.domain.CandleIssue;
import pl.piomin.signalmind.quality.domain.QualityIssue;
import pl.piomin.signalmind.stock.domain.Candle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects missing 1-minute candles within the NSE trading session window.
 *
 * <p>The NSE cash market session runs from 09:15 IST to 15:29 IST inclusive,
 * producing 375 expected minute-bars on every trading day.
 */
@Component
public class MarketSessionGapDetector implements DataQualityValidator {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final LocalTime SESSION_START = LocalTime.of(9, 15);
    public static final LocalTime SESSION_END = LocalTime.of(15, 29);
    public static final int EXPECTED_CANDLES = 375;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TradingCalendarService tradingCalendar;

    public MarketSessionGapDetector(TradingCalendarService tradingCalendar) {
        this.tradingCalendar = tradingCalendar;
    }

    @Override
    public String validatorName() {
        return "market-session-gap";
    }

    @Override
    public List<CandleIssue> validate(String symbol, LocalDate tradingDay, List<Candle> candles) {
        if (!tradingCalendar.isTradingDay(tradingDay)) {
            return List.of();
        }

        // Build the set of minute-truncated UTC instants that are actually present.
        Set<Instant> actual = new HashSet<>(candles.size() * 2);
        for (Candle candle : candles) {
            actual.add(candle.getCandleTime().truncatedTo(ChronoUnit.MINUTES));
        }

        // Build the set of expected minute instants from SESSION_START to SESSION_END (inclusive).
        List<CandleIssue> issues = new ArrayList<>();
        ZonedDateTime cursor = tradingDay.atTime(SESSION_START).atZone(IST);
        ZonedDateTime sessionEndTime = tradingDay.atTime(SESSION_END).atZone(IST);

        while (!cursor.isAfter(sessionEndTime)) {
            Instant expected = cursor.toInstant();
            if (!actual.contains(expected)) {
                String label = cursor.format(HH_MM);
                issues.add(new CandleIssue(
                        expected,
                        QualityIssue.MISSING_CANDLE,
                        "no candle at " + label + " IST"
                ));
            }
            cursor = cursor.plusMinutes(1);
        }

        return issues;
    }
}
