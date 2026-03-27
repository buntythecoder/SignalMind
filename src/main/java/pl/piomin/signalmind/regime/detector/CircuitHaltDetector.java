package pl.piomin.signalmind.regime.detector;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.regime.indicator.OhlcBar;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Detects {@link MarketRegime#CIRCUIT_HALT} (SM-21, priority 0).
 *
 * <p>Triggers if either of these conditions holds during trading hours
 * (09:15–15:30 IST, Monday–Friday):
 * <ol>
 *   <li>The most recent bar's timestamp is more than 10 minutes in the past, or</li>
 *   <li>The last 5 bars all have High == Low (zero price range — no trading activity).</li>
 * </ol>
 */
@Component
public class CircuitHaltDetector implements RegimeDetector {

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
    private static final ZoneId   IST              = ZoneId.of("Asia/Kolkata");
    private static final int      TRADING_START_H  = 9;
    private static final int      TRADING_START_M  = 15;
    private static final int      TRADING_END_H    = 15;
    private static final int      TRADING_END_M    = 30;
    private static final int      ZERO_RANGE_BARS  = 5;

    @Override
    public Optional<MarketRegime> detect(List<OhlcBar> bars,
                                         BigDecimal currentVix,
                                         BigDecimal vwap,
                                         Instant latestBarTime) {
        if (bars == null || bars.isEmpty() || latestBarTime == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();

        if (isDuringTradingHours(now)) {
            // Condition 1: stale data — no new candle for > 10 minutes
            if (Duration.between(latestBarTime, now).compareTo(STALE_THRESHOLD) > 0) {
                return Optional.of(MarketRegime.CIRCUIT_HALT);
            }

            // Condition 2: last ZERO_RANGE_BARS all have zero price range (circuit freeze)
            if (bars.size() >= ZERO_RANGE_BARS && allZeroRange(bars)) {
                return Optional.of(MarketRegime.CIRCUIT_HALT);
            }
        }

        return Optional.empty();
    }

    private boolean isDuringTradingHours(Instant now) {
        ZonedDateTime zdt = now.atZone(IST);
        int dayOfWeek = zdt.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        if (dayOfWeek > 5) {
            return false; // weekend
        }
        int totalMinutes = zdt.getHour() * 60 + zdt.getMinute();
        int start = TRADING_START_H * 60 + TRADING_START_M;
        int end   = TRADING_END_H   * 60 + TRADING_END_M;
        return totalMinutes >= start && totalMinutes <= end;
    }

    /** Returns true when the last {@link #ZERO_RANGE_BARS} bars all have High == Low. */
    private boolean allZeroRange(List<OhlcBar> bars) {
        int size = bars.size();
        for (int i = size - ZERO_RANGE_BARS; i < size; i++) {
            OhlcBar bar = bars.get(i);
            if (bar.high().compareTo(bar.low()) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public String detectorName() {
        return "CircuitHaltDetector";
    }
}
