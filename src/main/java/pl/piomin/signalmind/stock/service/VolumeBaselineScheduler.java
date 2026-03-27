package pl.piomin.signalmind.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers a nightly rebuild of all volume baselines after the NSE market
 * closes each weekday.
 *
 * <p>The cron fires at 15:45 IST Monday–Friday — 15 minutes after the session
 * ends (15:30 IST) — giving the data ingestion pipeline time to persist the
 * day's final candles before the baseline calculation begins.
 *
 * <p>The scheduler can be disabled at runtime via:
 * <pre>
 *   signalmind.baseline.scheduler.enabled=false
 * </pre>
 * Defaults to {@code true} when the property is absent.
 */
@Component
@ConditionalOnProperty(
        name = "signalmind.baseline.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class VolumeBaselineScheduler {

    private static final Logger log = LoggerFactory.getLogger(VolumeBaselineScheduler.class);

    private final VolumeBaselineService service;

    public VolumeBaselineScheduler(VolumeBaselineService service) {
        this.service = service;
    }

    /**
     * Post-market recalculation — runs at 15:45 IST, Monday to Friday.
     *
     * <p>Cron expression: {@code 0 45 15 * * MON-FRI} in the {@code Asia/Kolkata}
     * time zone, which Spring converts to the JVM's local scheduler.
     */
    @Scheduled(cron = "0 45 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void runNightly() {
        log.info("[baseline] Post-market recalculation triggered");
        service.recalculateAll();
        log.info("[baseline] Recalculation complete");
    }
}
