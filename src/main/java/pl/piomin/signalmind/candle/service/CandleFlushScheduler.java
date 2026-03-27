package pl.piomin.signalmind.candle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Scheduler that drives the 1-minute candle flush cycle and daily VWAP reset.
 *
 * <h3>Flush timing</h3>
 * <p>The cron expression {@code 2 * * * * MON-FRI} fires at second :02 of every
 * IST minute on weekdays. At that point the <em>previous</em> minute's slot
 * ({@code now − 1 min}, truncated to the minute boundary) is flushed.  The
 * 2-second gap acts as a late-tick buffer: any tick that arrives up to 1 second
 * late for minute HH:MM will have been received by HH:MM:01 and so will be
 * included in the accumulated candle before it is flushed at HH:MM+1:02.
 *
 * <h3>Daily VWAP reset</h3>
 * <p>VWAP is cumulative within a session (09:15–15:30 IST). The 09:15 cron resets
 * the per-stock VWAP state so that the first candle of the day starts a fresh
 * session accumulation.
 *
 * <p>Only activated when {@code angelone.ingestion.enabled=true}. Absent in all
 * test profiles.
 *
 * <p>SM-19
 */
@Component
@ConditionalOnProperty(name = "angelone.ingestion.enabled", matchIfMissing = false)
public class CandleFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(CandleFlushScheduler.class);

    @SuppressWarnings("unused")
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MinuteCandleAssembler assembler;

    public CandleFlushScheduler(MinuteCandleAssembler assembler) {
        this.assembler = assembler;
    }

    /**
     * Flush the previous minute's accumulated candle.
     *
     * <p>Fires at second :02 of every IST minute on weekdays. The 2-second
     * late-tick buffer means ticks from HH:MM:58–HH:MM:59.999 will still be
     * captured before this fires.
     */
    @Scheduled(cron = "2 * * * * MON-FRI", zone = "Asia/Kolkata")
    public void flushPreviousMinute() {
        Instant prevMinute = Instant.now()
                .truncatedTo(ChronoUnit.MINUTES)
                .minus(1, ChronoUnit.MINUTES);
        log.debug("[candle] Flushing minute slot {}", prevMinute);
        assembler.flushMinute(prevMinute);
    }

    /**
     * Reset per-stock VWAP accumulators at market open (09:15 IST) each weekday.
     *
     * <p>This ensures the cumulative VWAP starts fresh at the beginning of each
     * trading session rather than carrying over stale state from the previous day.
     */
    @Scheduled(cron = "0 15 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void resetDailyState() {
        assembler.resetDailyVwapState();
    }
}
