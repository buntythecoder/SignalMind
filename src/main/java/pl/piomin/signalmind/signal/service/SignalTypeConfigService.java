package pl.piomin.signalmind.signal.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.domain.SignalTypeConfig;
import pl.piomin.signalmind.signal.repository.SignalTypeConfigRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SM-27: Signal-type feature-flag cache.
 *
 * <p>Loads all {@link SignalTypeConfig} rows from the database at startup
 * and refreshes the cache every 5 minutes.  The engine calls
 * {@link #isEnabled(SignalType)} before invoking a detector so that a disabled
 * signal type takes effect within 5 minutes — no restart required.
 *
 * <p>If a signal type has no config row (e.g. a newly introduced type before
 * the row is seeded), it defaults to <strong>enabled</strong>.
 */
@Service
@ConditionalOnBean(SignalTypeConfigRepository.class)
public class SignalTypeConfigService {

    private static final Logger log = LoggerFactory.getLogger(SignalTypeConfigService.class);

    /** Refresh interval: 5 minutes in milliseconds. */
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    private final SignalTypeConfigRepository repository;

    /** In-memory cache: signalType → enabled. */
    private final Map<SignalType, Boolean> cache = new ConcurrentHashMap<>();

    public SignalTypeConfigService(SignalTypeConfigRepository repository) {
        this.repository = repository;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Populates the cache on application startup. */
    @PostConstruct
    public void loadOnStartup() {
        refresh();
    }

    /** Refreshes the cache from the database every 5 minutes. */
    @Scheduled(fixedDelay = REFRESH_INTERVAL_MS)
    public void refresh() {
        try {
            Map<SignalType, Boolean> fresh = new ConcurrentHashMap<>();
            for (SignalTypeConfig cfg : repository.findAll()) {
                fresh.put(cfg.getSignalType(), cfg.isEnabled());
            }
            cache.clear();
            cache.putAll(fresh);
            log.debug("[signal-type-config] Cache refreshed: {} entries", cache.size());
        } catch (Exception e) {
            log.warn("[signal-type-config] Refresh failed — retaining stale cache: {}", e.getMessage());
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given signal type is currently enabled.
     * Defaults to {@code true} when no config row exists (fail-open).
     *
     * @param type the signal type to check
     * @return {@code true} if the engine should run this detector
     */
    public boolean isEnabled(SignalType type) {
        return cache.getOrDefault(type, true);
    }

    /**
     * Returns a snapshot of the current cache for diagnostic purposes.
     */
    public Map<SignalType, Boolean> getCache() {
        return Map.copyOf(cache);
    }
}
