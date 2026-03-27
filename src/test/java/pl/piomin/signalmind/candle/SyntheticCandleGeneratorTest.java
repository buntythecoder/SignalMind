package pl.piomin.signalmind.candle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.candle.service.SyntheticCandleGenerator;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SyntheticCandleGenerator} (SM-20).
 *
 * <p>Pure unit tests — no Spring context or database required.
 */
@ExtendWith(MockitoExtension.class)
class SyntheticCandleGeneratorTest {

    @Mock
    private CandleRepository candleRepository;

    @InjectMocks
    private SyntheticCandleGenerator syntheticCandleGenerator;

    @Test
    @DisplayName("generate returns synthetic candle with O=H=L=C=prevClose and volume=0 when a previous candle exists")
    void generate_returnsSynthetic_whenPreviousExists() {
        // Arrange
        Stock stock = new Stock("RELIANCE", "Reliance Industries", IndexType.NIFTY50);
        Instant slotStart = Instant.parse("2025-01-15T03:45:00Z"); // 09:15 IST

        BigDecimal prevClose = new BigDecimal("100.00");
        Candle previousCandle = new Candle(stock, Instant.parse("2025-01-15T03:44:00Z"),
                new BigDecimal("99.00"), new BigDecimal("101.00"),
                new BigDecimal("98.50"), prevClose,
                1000L, CandleSource.LIVE,
                null, null, null, null);

        when(candleRepository.findLatestCandle(any())).thenReturn(Optional.of(previousCandle));

        // Act
        Optional<Candle> result = syntheticCandleGenerator.generate(stock, slotStart, null);

        // Assert
        assertTrue(result.isPresent(), "Should return a synthetic candle when previous candle exists");
        Candle synthetic = result.get();

        assertEquals(0, prevClose.compareTo(synthetic.getOpen()),
                "Open should equal prevClose");
        assertEquals(0, prevClose.compareTo(synthetic.getHigh()),
                "High should equal prevClose");
        assertEquals(0, prevClose.compareTo(synthetic.getLow()),
                "Low should equal prevClose");
        assertEquals(0, prevClose.compareTo(synthetic.getClose()),
                "Close should equal prevClose");
        assertEquals(0L, synthetic.getVolume(), "Volume should be 0");
        assertTrue(synthetic.isSynthetic(), "isSynthetic should be true");
        assertEquals(slotStart, synthetic.getCandleTime(), "candleTime should match slotStart");
    }

    @Test
    @DisplayName("generate returns empty when no previous candle exists for the stock")
    void generate_returnsEmpty_whenNoPreviousCandle() {
        // Arrange
        Stock stock = new Stock("TCS", "Tata Consultancy Services", IndexType.NIFTY50);
        Instant slotStart = Instant.parse("2025-01-15T03:45:00Z");

        when(candleRepository.findLatestCandle(any())).thenReturn(Optional.empty());

        // Act
        Optional<Candle> result = syntheticCandleGenerator.generate(stock, slotStart, null);

        // Assert
        assertTrue(result.isEmpty(), "Should return empty when no previous candle exists");
    }
}
