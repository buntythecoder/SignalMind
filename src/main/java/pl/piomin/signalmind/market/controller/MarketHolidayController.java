package pl.piomin.signalmind.market.controller;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.market.domain.MarketHoliday;
import pl.piomin.signalmind.market.repository.MarketHolidayRepository;
import pl.piomin.signalmind.market.service.MarketDayStateService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoints for the NSE market holiday calendar (SM-17).
 *
 * <pre>
 *   GET /api/admin/market/holidays                   — full calendar
 *   GET /api/admin/market/holidays/upcoming?days=30  — next N days
 *   GET /api/admin/market/today                      — today's trading status
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/market")
public class MarketHolidayController {

    private final MarketHolidayRepository holidayRepository;
    private final MarketDayStateService   marketDayState;

    public MarketHolidayController(MarketHolidayRepository holidayRepository,
                                   MarketDayStateService marketDayState) {
        this.holidayRepository = holidayRepository;
        this.marketDayState    = marketDayState;
    }

    /** Returns all holidays ordered by date. */
    @GetMapping("/holidays")
    public List<MarketHoliday> listAll() {
        return holidayRepository.findAll(Sort.by("holidayDate"));
    }

    /**
     * Returns holidays falling within the next {@code days} calendar days.
     * Default: 30 days. Maximum: 365 days.
     */
    @GetMapping("/holidays/upcoming")
    public ResponseEntity<List<MarketHoliday>> upcoming(
            @RequestParam(defaultValue = "30") int days) {

        if (days < 1 || days > 365) {
            return ResponseEntity.badRequest().build();
        }
        LocalDate from = LocalDate.now();
        LocalDate to   = from.plusDays(days);
        return ResponseEntity.ok(
                holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to));
    }

    /** Returns today's computed trading-day status (useful for dashboards). */
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> todayStatus() {
        LocalDate today = LocalDate.now();
        boolean isTrading = marketDayState.isTodayTradingDay();
        return ResponseEntity.ok(Map.of(
                "date",          today.toString(),
                "tradingDay",    isTrading,
                "message",       isTrading
                        ? "Market open today — 09:15 to 15:30 IST"
                        : "Market closed today"
        ));
    }
}
