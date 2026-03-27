package pl.piomin.signalmind.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.service.HistoricalDataProvider;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Batch processor: for each {@link Stock}, fetches one year of 1-minute
 * historical candles via the first supporting {@link HistoricalDataProvider},
 * saves them in bulk, and returns a summary for logging.
 *
 * <p>Fetching AND persisting happens here (not in the writer) because one stock
 * can produce up to ~94,000 candles — buffering all of them in the chunk write
 * buffer would be wasteful.
 *
 * <p>On any error, logs the failure and returns an {@link IngestionSummary}
 * with {@code success=false} rather than letting the exception propagate and
 * roll back the entire step. This keeps the job moving; a post-job report
 * identifies which stocks need to be retried.
 */
@Component
public class CandleBackfillProcessor implements ItemProcessor<Stock, IngestionSummary> {

    private static final Logger log = LoggerFactory.getLogger(CandleBackfillProcessor.class);

    /** How many calendar days of history to pull per stock. */
    static final int HISTORY_DAYS = 365;

    private final List<HistoricalDataProvider> providers;
    private final CandleRepository             candleRepository;

    public CandleBackfillProcessor(List<HistoricalDataProvider> providers,
                                   CandleRepository candleRepository) {
        this.providers        = providers;
        this.candleRepository = candleRepository;
    }

    @Override
    public IngestionSummary process(Stock stock) {
        String symbol = stock.getSymbol();

        HistoricalDataProvider provider = providers.stream()
                .filter(p -> p.supports(symbol))
                .findFirst()
                .orElse(null);

        if (provider == null) {
            log.warn("No provider supports {} — skipping", symbol);
            return IngestionSummary.failed(symbol, "no provider");
        }

        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = endDate.minusDays(HISTORY_DAYS - 1L);

        try {
            List<Candle> candles = provider.fetchCandles(symbol, startDate, endDate);

            if (!candles.isEmpty()) {
                candleRepository.saveAll(candles);
            }

            log.info("[batch] {} — {} candles fetched and saved", symbol, candles.size());
            return IngestionSummary.ok(symbol, candles.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return IngestionSummary.failed(symbol, "interrupted");
        } catch (Exception e) {
            log.error("[batch] Failed to backfill {}: {}", symbol, e.getMessage(), e);
            return IngestionSummary.failed(symbol, e.getMessage());
        }
    }
}
