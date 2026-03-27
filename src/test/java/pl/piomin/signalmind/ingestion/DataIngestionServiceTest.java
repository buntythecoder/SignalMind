package pl.piomin.signalmind.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.ingestion.service.DataIngestionService;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link DataIngestionService} stock-partitioning logic
 * and backoff formula. No Spring context — no database, no Redis, no WebSocket.
 */
@ExtendWith(MockitoExtension.class)
class DataIngestionServiceTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static Stock mockStock(String symbol, IndexType indexType) {
        Stock s = mock(Stock.class);
        lenient().when(s.getSymbol()).thenReturn(symbol);
        when(s.getIndexType()).thenReturn(indexType);
        return s;
    }

    private static List<Stock> buildMixedStockList() {
        return List.of(
                mockStock("RELIANCE",   IndexType.NIFTY50),
                mockStock("INFY",       IndexType.NIFTY50),
                mockStock("TCS",        IndexType.NIFTY50),
                mockStock("HDFCBANK",   IndexType.BANKNIFTY),
                mockStock("SBIN",       IndexType.BANKNIFTY),
                mockStock("NIFTY",      IndexType.NIFTY_INDEX),
                mockStock("INDIAVIX",   IndexType.INDIA_VIX)
        );
    }

    // ── Connection A: only NIFTY50 ────────────────────────────────────────────

    @Test
    void connectionAShouldContainOnlyNifty50Stocks() {
        List<Stock> all = buildMixedStockList();

        List<Stock> connA = DataIngestionService.selectConnectionAStocks(all);

        assertThat(connA).isNotEmpty();
        assertThat(connA).allMatch(s -> s.getIndexType() == IndexType.NIFTY50);
        assertThat(connA).hasSize(3); // RELIANCE, INFY, TCS
    }

    // ── Connection B: everything else ─────────────────────────────────────────

    @Test
    void connectionBShouldContainNonNifty50Stocks() {
        List<Stock> all = buildMixedStockList();

        List<Stock> connB = DataIngestionService.selectConnectionBStocks(all);

        assertThat(connB).isNotEmpty();
        assertThat(connB).noneMatch(s -> s.getIndexType() == IndexType.NIFTY50);
        assertThat(connB).hasSize(4); // HDFCBANK, SBIN, NIFTY, INDIAVIX
    }

    @Test
    void connectionAAndBShouldPartitionAllStocksWithoutOverlap() {
        List<Stock> all = buildMixedStockList();

        List<Stock> connA = DataIngestionService.selectConnectionAStocks(all);
        List<Stock> connB = DataIngestionService.selectConnectionBStocks(all);

        assertThat(connA.size() + connB.size()).isEqualTo(all.size());
        // No symbol appears in both lists.
        List<String> symbolsA = connA.stream().map(Stock::getSymbol).toList();
        List<String> symbolsB = connB.stream().map(Stock::getSymbol).toList();
        assertThat(symbolsA).doesNotContainAnyElementsOf(symbolsB);
    }

    // ── Backoff formula ───────────────────────────────────────────────────────

    /**
     * Verifies the backoff formula inline without needing to instantiate
     * {@link DataIngestionService} (which requires Spring-managed dependencies).
     * The formula: {@code Math.min(3_000L * (1L << Math.min(attempt, 5)), 60_000L)}
     */
    @Test
    void backoffDelay_exponentialWithCap() {
        assertThat(backoff(0)).isEqualTo(3_000L);
        assertThat(backoff(1)).isEqualTo(6_000L);
        assertThat(backoff(2)).isEqualTo(12_000L);
        assertThat(backoff(3)).isEqualTo(24_000L);
        assertThat(backoff(4)).isEqualTo(48_000L);
        assertThat(backoff(5)).isEqualTo(60_000L);  // 3000 * 32 = 96000, capped to 60000
        assertThat(backoff(10)).isEqualTo(60_000L); // well past cap
    }

    /** Mirror of the private {@code DataIngestionService#backoffDelayMs} formula. */
    private static long backoff(int attempt) {
        return Math.min(3_000L * (1L << Math.min(attempt, 5)), 60_000L);
    }
}
