package pl.piomin.signalmind.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Orchestrates historical candle ingestion across all registered
 * {@link HistoricalDataProvider} implementations.
 *
 * <h2>Adding a new provider</h2>
 * <ol>
 *   <li>Implement {@link HistoricalDataProvider} and annotate with {@code @Service}.</li>
 *   <li>Spring auto-discovers it; no changes needed here.</li>
 *   <li>Override {@link HistoricalDataProvider#supports} to restrict which symbols the
 *       provider handles (e.g. only those with an Angel One token).</li>
 * </ol>
 *
 * <h2>Provider selection</h2>
 * <p>By default, the first provider that {@linkplain HistoricalDataProvider#supports supports}
 * the symbol is used. Use {@link #bootstrapWithProvider} to target a specific provider by name.
 */
@Service
public class CandleIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CandleIngestionService.class);

    private final List<HistoricalDataProvider> providers;
    private final CandleRepository             candleRepository;

    public CandleIngestionService(List<HistoricalDataProvider> providers,
                                  CandleRepository candleRepository) {
        this.providers        = providers;
        this.candleRepository = candleRepository;
    }

    /**
     * Bootstraps historical candles for {@code symbol} using the first provider
     * that reports it can supply data for that symbol.
     *
     * @return number of candle rows inserted
     * @throws IllegalStateException if no provider supports the symbol
     * @throws InterruptedException  if a rate-limit wait is interrupted
     */
    @Transactional
    public int bootstrap(String symbol, LocalDate startDate, LocalDate endDate)
            throws InterruptedException {

        HistoricalDataProvider provider = providers.stream()
                .filter(p -> p.supports(symbol))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No historical data provider supports symbol: " + symbol));

        return ingest(provider, symbol, startDate, endDate);
    }

    /**
     * Bootstraps using a specific provider identified by {@link HistoricalDataProvider#providerName()}.
     *
     * @throws IllegalArgumentException if no provider with that name is registered
     */
    @Transactional
    public int bootstrapWithProvider(String providerName, String symbol,
                                     LocalDate startDate, LocalDate endDate)
            throws InterruptedException {

        HistoricalDataProvider provider = providers.stream()
                .filter(p -> p.providerName().equals(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown provider: " + providerName));

        return ingest(provider, symbol, startDate, endDate);
    }

    /**
     * Convenience: bootstrap the last {@code days} calendar days (today inclusive).
     */
    @Transactional
    public int bootstrapLastDays(String symbol, int days) throws InterruptedException {
        LocalDate today = LocalDate.now();
        return bootstrap(symbol, today.minusDays(days - 1L), today);
    }

    /** Lists the names of all registered providers, in priority order. */
    public List<String> availableProviders() {
        return providers.stream().map(HistoricalDataProvider::providerName).toList();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private int ingest(HistoricalDataProvider provider, String symbol,
                       LocalDate startDate, LocalDate endDate) throws InterruptedException {

        log.info("Ingesting candles for {} via {} [{} – {}]",
                symbol, provider.providerName(), startDate, endDate);

        List<Candle> candles = provider.fetchCandles(symbol, startDate, endDate);

        if (candles.isEmpty()) {
            log.info("No candles returned for {} from {}", symbol, provider.providerName());
            return 0;
        }

        try {
            candleRepository.saveAll(candles);
        } catch (Exception e) {
            // Duplicate candles (re-run case) — log and continue
            log.warn("Partial insert for {}/{}: {}", symbol, provider.providerName(), e.getMessage());
        }

        log.info("Inserted {} candles for {} via {}", candles.size(), symbol, provider.providerName());
        return candles.size();
    }
}
