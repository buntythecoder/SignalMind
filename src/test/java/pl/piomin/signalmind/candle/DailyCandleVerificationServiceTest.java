package pl.piomin.signalmind.candle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.candle.service.DailyCandleVerificationService;
import pl.piomin.signalmind.integration.telegram.TelegramAlertService;
import pl.piomin.signalmind.market.service.TradingCalendarService;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DailyCandleVerificationService} (SM-20).
 *
 * <p>Pure unit tests — no Spring context or database required.
 */
@ExtendWith(MockitoExtension.class)
class DailyCandleVerificationServiceTest {

    @Mock
    private CandleRepository candleRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private TelegramAlertService telegramAlertService;

    @Mock
    private TradingCalendarService tradingCalendarService;

    @InjectMocks
    private DailyCandleVerificationService verificationService;

    @Test
    @DisplayName("verifyDailyCandles sends alert when a stock has fewer than 375 candles")
    void verifyDailyCandles_sendsAlert_whenGapExists() {
        // Arrange — trading day, one stock with only 370 candles
        Stock reliance = stockWithId(1L, "RELIANCE");

        when(tradingCalendarService.isTradingDay(any())).thenReturn(true);
        when(stockRepository.findAllByActiveTrue()).thenReturn(List.of(reliance));
        // Return 370 candles for stock id=1
        List<Object[]> counts370 = new ArrayList<>();
        counts370.add(new Object[]{1L, 370L});
        when(candleRepository.countCandlesPerStockBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(counts370);

        // Act
        verificationService.verifyDailyCandles();

        // Assert
        verify(telegramAlertService, times(1)).sendAlert(anyString());
    }

    @Test
    @DisplayName("verifyDailyCandles does not send alert when all stocks have exactly 375 candles")
    void verifyDailyCandles_noAlert_whenAllComplete() {
        // Arrange — trading day, one stock with exactly 375 candles
        Stock tcs = stockWithId(2L, "TCS");

        when(tradingCalendarService.isTradingDay(any())).thenReturn(true);
        when(stockRepository.findAllByActiveTrue()).thenReturn(List.of(tcs));
        List<Object[]> counts375 = new ArrayList<>();
        counts375.add(new Object[]{2L, 375L});
        when(candleRepository.countCandlesPerStockBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(counts375);

        // Act
        verificationService.verifyDailyCandles();

        // Assert
        verify(telegramAlertService, never()).sendAlert(anyString());
    }

    @Test
    @DisplayName("verifyDailyCandles skips all processing on a non-trading day")
    void verifyDailyCandles_skips_onNonTradingDay() {
        // Arrange — not a trading day
        when(tradingCalendarService.isTradingDay(any())).thenReturn(false);

        // Act
        verificationService.verifyDailyCandles();

        // Assert — no repository queries should be made
        verify(candleRepository, never()).countCandlesPerStockBetween(any(), any());
        verify(stockRepository, never()).findAllByActiveTrue();
        verify(telegramAlertService, never()).sendAlert(anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a Stock and reflectively sets its id, since the protected constructor
     * does not accept an id parameter and only JPA sets it on persist.
     */
    private Stock stockWithId(Long id, String symbol) {
        Stock stock = new Stock(symbol, symbol + " Ltd", IndexType.NIFTY50);
        try {
            var field = Stock.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(stock, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set Stock.id for test: " + e.getMessage(), e);
        }
        return stock;
    }
}
