package pl.piomin.signalmind.integration.breeze;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.integration.breeze.dto.BreezeOhlcv;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;
import pl.piomin.signalmind.stock.service.HistoricalDataProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link HistoricalDataProvider} backed by the ICICI Breeze REST API.
 *
 * <p>Fetches 1-minute OHLCV candles via {@link BreezeClient} and converts them
 * into transient {@link Candle} objects. Persistence is handled upstream by
 * {@link pl.piomin.signalmind.stock.service.CandleIngestionService}.
 *
 * <p>Day-by-day chunking keeps each request small (Breeze rejects large ranges)
 * and gives the rate limiter a chance to pace between chunks.
 */
@Service
public class BreezeHistoricalService implements HistoricalDataProvider {

    private static final Logger log = LoggerFactory.getLogger(BreezeHistoricalService.class);

    // Breeze returns timestamps like "2024-01-15 09:15:00" (local IST, no offset)
    private static final DateTimeFormatter BREEZE_DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // IST = UTC+5:30
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private static final String EXCHANGE_NSE = "NSE";

    private final BreezeClient    breezeClient;
    private final StockRepository stockRepository;

    public BreezeHistoricalService(BreezeClient breezeClient, StockRepository stockRepository) {
        this.breezeClient    = breezeClient;
        this.stockRepository = stockRepository;
    }

    @Override
    public String providerName() {
        return "icici-breeze";
    }

    @Override
    public boolean supports(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .map(s -> s.getBreezeCode() != null && !s.getBreezeCode().isBlank())
                .orElse(false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Splits the range into single-day chunks; fires one Breeze API call per day.
     *
     * @throws IllegalArgumentException if {@code symbol} is not in the database
     */
    @Override
    public List<Candle> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate)
            throws InterruptedException {

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));

        if (stock.getBreezeCode() == null || stock.getBreezeCode().isBlank()) {
            log.warn("Stock {} has no breeze_code — returning empty", symbol);
            return List.of();
        }

        List<Candle> result = new ArrayList<>();
        LocalDate cursor = startDate;

        while (!cursor.isAfter(endDate)) {
            Instant from = cursor.atStartOfDay().toInstant(IST);
            Instant to   = cursor.plusDays(1).atStartOfDay().toInstant(IST);

            List<BreezeOhlcv> bars =
                    breezeClient.fetchHistoricalCandles(stock.getBreezeCode(), EXCHANGE_NSE, from, to);

            for (BreezeOhlcv bar : bars) {
                Instant candleTime = parseCandleTime(bar.datetime());
                if (candleTime == null) continue;

                result.add(new Candle(stock, candleTime,
                        bar.open(), bar.high(), bar.low(), bar.close(),
                        bar.volume(), CandleSource.HIST));
            }

            cursor = cursor.plusDays(1);
        }

        log.info("[{}] Fetched {} candles for {} [{} – {}]",
                providerName(), result.size(), symbol, startDate, endDate);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Instant parseCandleTime(String datetime) {
        if (datetime == null || datetime.isBlank()) return null;
        try {
            return LocalDateTime.parse(datetime.trim(), BREEZE_DT_FMT).toInstant(IST);
        } catch (DateTimeParseException e) {
            log.warn("Unparseable candle datetime '{}': {}", datetime, e.getMessage());
            return null;
        }
    }
}
