package pl.piomin.signalmind.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.piomin.signalmind.market.service.TradingCalendarService;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.domain.VolumeBaseline;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;
import pl.piomin.signalmind.stock.repository.VolumeBaselineRepository;
import pl.piomin.signalmind.stock.service.VolumeBaselineService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VolumeBaselineService}.
 *
 * <p>No Spring context — pure Mockito wiring via constructor injection.
 */
@ExtendWith(MockitoExtension.class)
class VolumeBaselineServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Mock
    private VolumeBaselineRepository baselineRepository;

    @Mock
    private CandleRepository candleRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private TradingCalendarService tradingCalendar;

    @Mock
    private Stock stock;

    private VolumeBaselineService service;

    @BeforeEach
    void setUp() {
        service = new VolumeBaselineService(
                baselineRepository, candleRepository, stockRepository, tradingCalendar);

        when(stock.getId()).thenReturn(1L);
        when(stock.getSymbol()).thenReturn("RELIANCE");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a candle at the given IST hour and minute with the specified volume.
     * Stock is null because the service only calls {@code candle.getCandleTime()}
     * and {@code candle.getVolume()}.
     */
    private Candle candleAt(int istHour, int istMinute, long volume) {
        Instant candleTime = LocalDate.of(2025, 1, 6) // arbitrary date — just needs to be a valid IST time
                .atTime(istHour, istMinute)
                .atZone(IST)
                .toInstant();
        return new Candle(
                null,
                candleTime,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                volume,
                CandleSource.HIST
        );
    }

    /**
     * Builds a list of 20 × 2 candles: one at 09:30 IST and one at 13:00 IST for
     * each of the 20 working days used in the window, using arbitrary dates
     * (the service groups by slot time, not by date).
     */
    private List<Candle> buildTwentyDayCandleList() {
        List<Candle> candles = new ArrayList<>(40);
        for (int i = 0; i < VolumeBaselineService.WINDOW_DAYS; i++) {
            candles.add(candleAt(9, 30, 500_000L));
            candles.add(candleAt(13, 0, 50_000L));
        }
        return candles;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Given 20 days of data with high morning volume (09:30) and low afternoon
     * volume (13:00), the service must persist correct per-slot averages and the
     * morning slot must be at least 5× the afternoon slot.
     */
    @Test
    @SuppressWarnings("unchecked")
    void morningVolumeHigherThanAfternoon_slotLogicWorks() {
        // Arrange — all days are trading days
        when(tradingCalendar.isTradingDay(any(LocalDate.class))).thenReturn(true);

        List<Candle> candles = buildTwentyDayCandleList();
        when(candleRepository.findByStockAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(candles);

        doNothing().when(baselineRepository).deleteAllByStock(any());

        when(baselineRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.recalculate(stock);

        // Capture the list passed to saveAll
        ArgumentCaptor<List<VolumeBaseline>> captor = ArgumentCaptor.forClass(List.class);
        verify(baselineRepository).saveAll(captor.capture());

        List<VolumeBaseline> saved = captor.getValue();

        // Assert — two distinct slots must be present
        assertThat(saved).hasSize(2);

        VolumeBaseline morning = saved.stream()
                .filter(b -> b.getSlotTime().equals(LocalTime.of(9, 30)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("09:30 baseline not found"));

        VolumeBaseline afternoon = saved.stream()
                .filter(b -> b.getSlotTime().equals(LocalTime.of(13, 0)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("13:00 baseline not found"));

        assertThat(morning.getAvgVolume())
                .as("Morning avg volume should equal 500,000")
                .isEqualTo(500_000L);

        assertThat(afternoon.getAvgVolume())
                .as("Afternoon avg volume should equal 50,000")
                .isEqualTo(50_000L);

        assertThat(morning.getAvgVolume())
                .as("Morning avg should be at least 5× the afternoon avg")
                .isGreaterThanOrEqualTo(afternoon.getAvgVolume() * 5);
    }

    /**
     * When the candle repository returns an empty list (no data available),
     * the service should still call delete (to clear stale data) and then
     * call saveAll with an empty collection — resulting in zero baselines persisted.
     */
    @Test
    @SuppressWarnings("unchecked")
    void emptyCandles_savesNothing() {
        // Arrange
        when(tradingCalendar.isTradingDay(any(LocalDate.class))).thenReturn(true);
        when(candleRepository.findByStockAndTimeRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        doNothing().when(baselineRepository).deleteAllByStock(any());
        when(baselineRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.recalculate(stock);

        // Capture saved list
        ArgumentCaptor<List<VolumeBaseline>> captor = ArgumentCaptor.forClass(List.class);
        verify(baselineRepository).deleteAllByStock(stock);
        verify(baselineRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .as("No baselines should be saved when candle data is empty")
                .isEmpty();
    }
}
