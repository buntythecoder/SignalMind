package pl.piomin.signalmind.market.service;

import org.springframework.stereotype.Service;
import pl.piomin.signalmind.market.repository.MarketHolidayRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Determines whether a given calendar date is a valid NSE trading day
 * and how many 1-minute candles are expected for that day.
 *
 * <p>NSE session: 09:15 IST (inclusive) to 15:29 IST (inclusive) = 375 minutes.
 */
@Service
public class TradingCalendarService {

    private final MarketHolidayRepository marketHolidayRepository;

    public TradingCalendarService(MarketHolidayRepository marketHolidayRepository) {
        this.marketHolidayRepository = marketHolidayRepository;
    }

    /**
     * Returns {@code true} if {@code date} is a working NSE trading day.
     *
     * <ul>
     *   <li>Saturday or Sunday → {@code false}</li>
     *   <li>Declared NSE holiday → {@code false}</li>
     *   <li>Otherwise → {@code true}</li>
     * </ul>
     */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !marketHolidayRepository.existsByHolidayDate(date);
    }

    /**
     * Returns the number of 1-minute candles expected for {@code date}.
     *
     * <ul>
     *   <li>Trading day → 375 (09:15 to 15:29 IST inclusive)</li>
     *   <li>Non-trading day → 0</li>
     * </ul>
     */
    public int expectedCandleCount(LocalDate date) {
        return isTradingDay(date) ? 375 : 0;
    }
}
