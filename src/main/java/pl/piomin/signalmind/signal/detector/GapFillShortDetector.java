package pl.piomin.signalmind.signal.detector;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detects Gap Fill Short (Gap Up) signals (SM-25).
 *
 * <p>Triggered when today's opening price is <strong>above</strong> the previous
 * session's close by 0.5%–3.0% (an upward gap).  The expectation is that
 * the price will fill the gap by returning to the previous close (SHORT direction).
 *
 * <p><strong>Broker note:</strong> Short-selling an equity intraday (MIS order type)
 * is required.  The Telegram alert includes the note:
 * <em>"Requires intraday short-selling (MIS). Verify with your broker."</em>
 *
 * <p>Conditions:
 * <ol>
 *   <li>Gap % = (todayOpen − prevClose) / prevClose × 100 is in [0.5, 3.0]</li>
 *   <li>First-candle volume &ge; 2.0&times; time-slot baseline</li>
 *   <li>Candle time is within the valid window: 09:15–10:30 IST</li>
 * </ol>
 *
 * <p>Pricing formula (PRD §6.7):
 * <ul>
 *   <li>Entry  = first candle close</li>
 *   <li>SL     = first candle high (gap extends further → trade invalidated)</li>
 *   <li>T1     = prevClose (the gap-fill level)</li>
 *   <li>T2     = prevClose − risk (symmetric extension)</li>
 * </ul>
 *
 * <p>The signal is valid for 15 minutes after generation.
 */
@Component
public class GapFillShortDetector implements SignalDetector {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final LocalTime WINDOW_START = LocalTime.of(9, 15);
    private static final LocalTime WINDOW_END   = LocalTime.of(10, 30);

    private static final BigDecimal GAP_MIN     = new BigDecimal("0.5");
    private static final BigDecimal GAP_MAX     = new BigDecimal("3.0");
    private static final BigDecimal VOLUME_MULT = new BigDecimal("2.0");
    private static final BigDecimal HUNDRED     = new BigDecimal("100");

    /** Broker note appended to Telegram alerts for this signal type. */
    public static final String BROKER_NOTE =
            "Requires intraday short-selling (MIS). Verify with your broker.";

    private static final int MAX_SIGNALS_PER_DAY = 1;

    private final CandleRepository candleRepository;

    public GapFillShortDetector(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    @Override
    public Optional<Signal> detect(Stock stock, List<Candle> todayCandles,
                                    Map<LocalTime, Long> volumeBaselines,
                                    String regime) {
        if (todayCandles.isEmpty()) return Optional.empty();

        // Resolve session start anchor
        Candle firstCandle = todayCandles.get(0);
        Instant sessionStart = firstCandle.getCandleTime()
                .atZone(IST).toLocalDate().atStartOfDay(IST).toInstant();

        Optional<Candle> prevOpt = candleRepository.findPrevSessionClose(
                stock.getId(), sessionStart);
        if (prevOpt.isEmpty()) return Optional.empty();

        return detectWithPrevClose(stock, todayCandles, volumeBaselines, regime,
                prevOpt.get().getClose());
    }

    /**
     * Core detection logic with an explicit prevClose argument.
     * Package-private to allow direct invocation in unit tests without a real CandleRepository.
     */
    Optional<Signal> detectWithPrevClose(Stock stock, List<Candle> todayCandles,
                                          Map<LocalTime, Long> volumeBaselines,
                                          String regime,
                                          BigDecimal prevClose) {
        for (Candle candle : todayCandles) {
            // Skip synthetic candles
            if (candle.isSynthetic()) continue;

            // Enforce detection window 09:15–10:30 IST
            LocalTime candleTime = candle.getCandleTime().atZone(IST).toLocalTime();
            if (candleTime.isBefore(WINDOW_START) || candleTime.isAfter(WINDOW_END)) continue;

            BigDecimal todayOpen = candle.getOpen();

            // Condition 1: gap % in [0.5, 3.0] (gap-up)
            BigDecimal gapPct = todayOpen.subtract(prevClose)
                    .divide(prevClose, 6, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            if (gapPct.compareTo(GAP_MIN) < 0 || gapPct.compareTo(GAP_MAX) > 0) continue;

            // Condition 2: volume >= 2.0× baseline
            Long baseline = volumeBaselines.get(candleTime);
            if (!volumeOk(candle.getVolume(), baseline)) continue;

            // All conditions met — build signal
            return buildSignal(stock, candle, prevClose, regime);
        }
        return Optional.empty();
    }

    private Optional<Signal> buildSignal(Stock stock, Candle candle,
                                          BigDecimal prevClose, String regime) {
        BigDecimal entry    = candle.getClose().setScale(2, RoundingMode.HALF_UP);
        BigDecimal stopLoss = candle.getHigh().setScale(2, RoundingMode.HALF_UP);
        BigDecimal risk     = stopLoss.subtract(entry);

        if (risk.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        BigDecimal t1 = prevClose.setScale(2, RoundingMode.HALF_UP); // gap-fill target
        BigDecimal t2 = t1.subtract(risk).setScale(2, RoundingMode.HALF_UP); // symmetric extension

        int regimeModifier = regime != null ? parseModifier(regime) : 0;
        int confidence = Math.max(0, Math.min(100, 50 + regimeModifier));

        Signal signal = new Signal(
                stock, SignalType.GAP_FILL_SHORT, SignalDirection.SHORT,
                entry, t1, t2, stopLoss,
                confidence, regime != null ? regime : "SIDEWAYS",
                candle.getCandleTime(),
                candle.getCandleTime().plus(15, ChronoUnit.MINUTES),
                null, null
        );
        return Optional.of(signal);
    }

    private boolean volumeOk(long volume, Long baseline) {
        if (baseline == null || baseline == 0L) return true;
        return BigDecimal.valueOf(volume)
                .compareTo(BigDecimal.valueOf(baseline).multiply(VOLUME_MULT)) >= 0;
    }

    private int parseModifier(String regime) {
        try {
            return MarketRegime.valueOf(regime).confidenceModifier();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    @Override public String detectorName() { return "GapFillShortDetector"; }
    @Override public SignalType signalType() { return SignalType.GAP_FILL_SHORT; }
    @Override public int maxSignalsPerDay() { return MAX_SIGNALS_PER_DAY; }
}
