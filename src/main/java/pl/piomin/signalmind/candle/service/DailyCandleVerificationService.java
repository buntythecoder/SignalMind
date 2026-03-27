package pl.piomin.signalmind.candle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.integration.telegram.TelegramAlertService;
import pl.piomin.signalmind.market.service.TradingCalendarService;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Performs a daily cross-check at 15:35 IST to verify that each active stock
 * has exactly 375 one-minute candles for the trading day (SM-20).
 *
 * <p>NSE session 09:15–15:29 IST = 375 minutes. Any stock with fewer candles
 * after session close indicates missing data (real or synthetic) and warrants
 * an operational alert.
 *
 * <p>The check is skipped on non-trading days (weekends and NSE holidays).
 */
@Service
public class DailyCandleVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DailyCandleVerificationService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int EXPECTED_CANDLES = 375;

    private final CandleRepository candleRepository;
    private final StockRepository stockRepository;
    private final TelegramAlertService telegram;
    private final TradingCalendarService tradingCalendar;

    public DailyCandleVerificationService(CandleRepository candleRepository,
                                          StockRepository stockRepository,
                                          TelegramAlertService telegram,
                                          TradingCalendarService tradingCalendar) {
        this.candleRepository = candleRepository;
        this.stockRepository  = stockRepository;
        this.telegram         = telegram;
        this.tradingCalendar  = tradingCalendar;
    }

    /**
     * Runs at 15:35 IST on weekdays. Queries candle counts per stock for today's
     * session window and alerts via Telegram if any stock is short on candles.
     */
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void verifyDailyCandles() {
        LocalDate today = LocalDate.now(IST);
        if (!tradingCalendar.isTradingDay(today)) {
            log.info("[verify] {} is not a trading day — skipping daily candle check", today);
            return;
        }

        // NSE session window: 09:15 IST to 15:30 IST
        Instant sessionStart = today.atTime(9, 15).atZone(IST).toInstant();
        Instant sessionEnd   = today.atTime(15, 30).atZone(IST).toInstant();

        List<Object[]> counts = candleRepository.countCandlesPerStockBetween(sessionStart, sessionEnd);
        Map<Long, Long> countByStockId = counts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        List<Stock> activeStocks = stockRepository.findAllByActiveTrue();
        List<String> gaps = new ArrayList<>();

        for (Stock stock : activeStocks) {
            long count = countByStockId.getOrDefault(stock.getId(), 0L);
            if (count < EXPECTED_CANDLES) {
                gaps.add(stock.getSymbol() + "=" + count);
                log.warn("[verify] {} has only {}/{} candles for {}",
                        stock.getSymbol(), count, EXPECTED_CANDLES, today);
            }
        }

        if (gaps.isEmpty()) {
            log.info("[verify] Daily candle check PASSED: all {} stocks have {} candles for {}",
                    activeStocks.size(), EXPECTED_CANDLES, today);
        } else {
            String msg = "⚠️ SignalMind daily candle gap: "
                    + gaps.size() + " stocks short on " + today + ": " + gaps;
            log.error("[verify] {}", msg);
            telegram.sendAlert(msg);
        }
    }
}
