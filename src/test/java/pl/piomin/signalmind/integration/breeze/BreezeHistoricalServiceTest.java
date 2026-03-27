package pl.piomin.signalmind.integration.breeze;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.integration.breeze.dto.BreezeOhlcv;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BreezeHistoricalServiceTest {

    @Mock BreezeClient    breezeClient;
    @Mock StockRepository stockRepository;

    BreezeHistoricalService service;

    Stock reliance;

    @BeforeEach
    void setup() {
        service = new BreezeHistoricalService(breezeClient, stockRepository);

        reliance = new Stock("RELIANCE", "Reliance Industries", IndexType.NIFTY50);
        reliance.setBreezeCode("RELIANCE");
    }

    @Test
    void providerName_isIciciBreeze() {
        assertThat(service.providerName()).isEqualTo("icici-breeze");
    }

    @Test
    void supports_trueWhenBreezeCodePresent() {
        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(reliance));
        assertThat(service.supports("RELIANCE")).isTrue();
    }

    @Test
    void supports_falseWhenBreezeCodeAbsent() {
        Stock noBreezeCode = new Stock("NOIDX", "No Breeze", IndexType.NIFTY50);
        when(stockRepository.findBySymbol("NOIDX")).thenReturn(Optional.of(noBreezeCode));
        assertThat(service.supports("NOIDX")).isFalse();
    }

    @Test
    void supports_falseWhenSymbolNotInDb() {
        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());
        assertThat(service.supports("UNKNOWN")).isFalse();
    }

    @Test
    void fetchCandles_throwsForUnknownSymbol() {
        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchCandles("UNKNOWN",
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown symbol");
    }

    @Test
    void fetchCandles_returnsEmptyForStockWithNoBreezeCode() throws InterruptedException {
        Stock noBreezeCode = new Stock("NOIDX", "No Breeze", IndexType.NIFTY50);
        when(stockRepository.findBySymbol("NOIDX")).thenReturn(Optional.of(noBreezeCode));

        List<Candle> result = service.fetchCandles("NOIDX",
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15));

        assertThat(result).isEmpty();
        verify(breezeClient, never()).fetchHistoricalCandles(any(), any(), any(), any());
    }

    @Test
    void fetchCandles_returnsCandles() throws InterruptedException {
        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(reliance));

        BreezeOhlcv bar = new BreezeOhlcv(
                "2024-01-15 09:15:00",
                new BigDecimal("2500.00"),
                new BigDecimal("2510.00"),
                new BigDecimal("2495.00"),
                new BigDecimal("2505.00"),
                150_000L);

        when(breezeClient.fetchHistoricalCandles(anyString(), anyString(), any(), any()))
                .thenReturn(List.of(bar));

        List<Candle> result = service.fetchCandles("RELIANCE",
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15));

        assertThat(result).hasSize(1);
        Candle c = result.getFirst();
        assertThat(c.getStock()).isSameAs(reliance);
        assertThat(c.getOpen()).isEqualByComparingTo("2500.00");
        assertThat(c.getClose()).isEqualByComparingTo("2505.00");
        assertThat(c.getVolume()).isEqualTo(150_000L);
        assertThat(c.getSource()).isEqualTo(CandleSource.HIST);
    }

    @Test
    void fetchCandles_skipsUnparseableDatetime() throws InterruptedException {
        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(reliance));

        BreezeOhlcv badBar = new BreezeOhlcv(
                "NOT-A-DATE",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 0L);

        when(breezeClient.fetchHistoricalCandles(anyString(), anyString(), any(), any()))
                .thenReturn(List.of(badBar));

        List<Candle> result = service.fetchCandles("RELIANCE",
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15));

        assertThat(result).isEmpty();
    }

    @Test
    void fetchCandles_firesOneDayChunkPerCalendarDay() throws InterruptedException {
        when(stockRepository.findBySymbol("RELIANCE")).thenReturn(Optional.of(reliance));
        when(breezeClient.fetchHistoricalCandles(anyString(), anyString(), any(), any()))
                .thenReturn(List.of());

        // 3-day range → 3 fetchHistoricalCandles calls
        service.fetchCandles("RELIANCE",
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 17));

        verify(breezeClient, times(3))
                .fetchHistoricalCandles(anyString(), anyString(), any(), any());
    }
}
