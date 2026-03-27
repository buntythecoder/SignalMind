package pl.piomin.signalmind.market.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Evaluates and holds today's trading-day status (SM-17).
 *
 * <p>The state is initialised at application startup via {@link ApplicationRunner}
 * and refreshed every weekday at 08:30 IST (before the WebSocket connection
 * attempt at ~08:45 IST).
 *
 * <p>Other services (WebSocket connector, morning-message scheduler) read
 * {@link #isTodayTradingDay()} instead of calling the DB every time.
 */
@Service
public class MarketDayStateService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketDayStateService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final TradingCalendarService tradingCalendar;
    private final AtomicBoolean tradingDay = new AtomicBoolean(false);

    public MarketDayStateService(TradingCalendarService tradingCalendar) {
        this.tradingCalendar = tradingCalendar;
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    /**
     * Evaluate today's status on startup so other beans can read
     * {@link #isTodayTradingDay()} immediately without waiting for 08:30.
     */
    @Override
    public void run(ApplicationArguments args) {
        evaluateTodayTradingStatus();
    }

    // ── Scheduled checks ──────────────────────────────────────────────────────

    /**
     * Pre-market gate: re-evaluate at 08:30 IST every weekday.
     * If today is a holiday the WebSocket connection must be skipped.
     */
    @Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void preMarketCheck() {
        evaluateTodayTradingStatus();
    }

    /**
     * Placeholder hook for the 09:10 IST morning message (SM-17 AC-3).
     * When the notification service is implemented, it should check
     * {@link #isTodayTradingDay()} before firing the message.
     */
    @Scheduled(cron = "0 10 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void morningMessageGate() {
        if (!tradingDay.get()) {
            log.info("[market] 09:10 gate — today is not a trading day; morning message suppressed");
        } else {
            log.debug("[market] 09:10 gate — trading day confirmed; morning message may proceed");
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if today (IST) is an NSE trading day.
     * Thread-safe; backed by an {@link AtomicBoolean}.
     */
    public boolean isTodayTradingDay() {
        return tradingDay.get();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void evaluateTodayTradingStatus() {
        LocalDate today = LocalDate.now(IST);
        boolean isTrading = tradingCalendar.isTradingDay(today);
        tradingDay.set(isTrading);

        if (isTrading) {
            log.info("[market] {} is a TRADING day — market session active at 09:15 IST", today);
        } else {
            log.info("[market] {} is NOT a trading day — WebSocket connection will be skipped", today);
        }
    }
}
