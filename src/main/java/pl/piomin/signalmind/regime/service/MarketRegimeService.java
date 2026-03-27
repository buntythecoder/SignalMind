package pl.piomin.signalmind.regime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.regime.detector.RegimeDetector;
import pl.piomin.signalmind.regime.domain.MarketRegime;
import pl.piomin.signalmind.regime.domain.RegimeSnapshot;
import pl.piomin.signalmind.regime.indicator.OhlcBar;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates market regime classification for Nifty 50 (SM-21).
 *
 * <p>Runs every 5 minutes during trading hours (09:15–15:30 IST, Mon–Fri) via
 * a {@link Scheduled} cron. The computed {@link RegimeSnapshot} is cached in
 * Redis under the key {@code regime:current} with a 10-minute TTL.
 *
 * <p>Conditional on {@link StringRedisTemplate} — absent in the no-Redis test
 * profile, which keeps {@link pl.piomin.signalmind.SignalMindApplicationTests}
 * clean without additional {@code @MockBean} declarations.
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class MarketRegimeService {

    private static final Logger log = LoggerFactory.getLogger(MarketRegimeService.class);

    private static final String   NIFTY_SYMBOL = "NIFTY";
    private static final String   VIX_SYMBOL   = "INDIAVIX";
    private static final String   REDIS_KEY    = "regime:current";
    private static final int      BAR_COUNT    = 50;
    private static final Duration VIX_STALE    = Duration.ofMinutes(5);
    private static final Duration REDIS_TTL    = Duration.ofMinutes(10);

    private final StockRepository     stockRepository;
    private final CandleRepository    candleRepository;
    private final List<RegimeDetector> detectors;
    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    public MarketRegimeService(StockRepository stockRepository,
                               CandleRepository candleRepository,
                               List<RegimeDetector> detectors,
                               StringRedisTemplate redis,
                               ObjectMapper objectMapper) {
        this.stockRepository  = stockRepository;
        this.candleRepository = candleRepository;
        this.detectors        = detectors;
        this.redis            = redis;
        this.objectMapper     = objectMapper;
    }

    // ── Scheduled classification ─────────────────────────────────────────────

    /**
     * Classifies the current regime every 5 minutes during trading hours.
     * Cron runs at second=0 of every 5th minute from 09:15 to 15:30 IST, Mon–Fri.
     */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void classify() {
        try {
            RegimeSnapshot snapshot = compute();
            storeInRedis(snapshot);
            log.info("[regime] {} — {}", snapshot.regime(), snapshot.reason());
        } catch (Exception e) {
            log.error("[regime] Classification failed: {}", e.getMessage(), e);
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the most recently cached regime snapshot from Redis, or
     * {@link Optional#empty()} when the key is absent or cannot be deserialised.
     */
    public Optional<RegimeSnapshot> currentRegime() {
        String json = redis.opsForValue().get(REDIS_KEY);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RegimeSnapshot.class));
        } catch (Exception e) {
            log.warn("[regime] Failed to deserialise snapshot from Redis: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Performs a fresh synchronous regime computation without touching Redis.
     * Called by {@link #classify()} and exposed for on-demand recalculation.
     *
     * @return computed {@link RegimeSnapshot}; never {@code null}
     */
    public RegimeSnapshot compute() {
        // 1. Resolve Nifty50 and VIX stocks
        Stock nifty = stockRepository.findBySymbol(NIFTY_SYMBOL)
                .orElseThrow(() -> new IllegalStateException(
                        "NIFTY stock not found — ensure seed data is loaded"));

        Stock vixStock = stockRepository.findBySymbol(VIX_SYMBOL).orElse(null);

        // 2. Load the last BAR_COUNT Nifty50 1-min candles (newest first from repo)
        List<Candle> rawCandles = candleRepository.findLatestByStock(nifty.getId(), BAR_COUNT);
        if (rawCandles.size() < 20) {
            return new RegimeSnapshot(
                    MarketRegime.SIDEWAYS, Instant.now(),
                    "Insufficient candle data (" + rawCandles.size() + " bars)");
        }

        // Reverse to oldest-first for all indicator calculations
        List<Candle> ordered = new ArrayList<>(rawCandles);
        Collections.reverse(ordered);

        List<OhlcBar> bars = ordered.stream()
                .map(c -> new OhlcBar(c.getOpen(), c.getHigh(), c.getLow(), c.getClose()))
                .toList();

        // 3. Resolve VIX (null when stale)
        BigDecimal vixClose = resolveVix(vixStock);

        // 4. VWAP from the latest candle (may be null for HIST-source candles)
        BigDecimal vwap = ordered.get(ordered.size() - 1).getVwap();

        // 5. Timestamp of the most recent bar
        Instant latestBarTime = ordered.get(ordered.size() - 1).getCandleTime();

        // 6. Run detectors in priority order — first match wins
        List<RegimeDetector> sorted = detectors.stream()
                .sorted(Comparator.comparingInt(RegimeDetector::priority))
                .toList();

        for (RegimeDetector detector : sorted) {
            Optional<MarketRegime> result = detector.detect(bars, vixClose, vwap, latestBarTime);
            if (result.isPresent()) {
                return new RegimeSnapshot(result.get(), Instant.now(), detector.detectorName());
            }
        }

        // Ultimate fallback — no detector fired
        return new RegimeSnapshot(
                MarketRegime.SIDEWAYS, Instant.now(),
                "No detector matched — defaulting to SIDEWAYS");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private BigDecimal resolveVix(Stock vixStock) {
        if (vixStock == null) {
            return null;
        }
        List<Candle> vixCandles = candleRepository.findLatestByStock(vixStock.getId(), 1);
        if (vixCandles.isEmpty()) {
            return null;
        }
        Candle lastVix = vixCandles.get(0);
        if (Instant.now().minus(VIX_STALE).isBefore(lastVix.getCandleTime())) {
            return lastVix.getClose();
        }
        log.debug("[regime] VIX data stale ({}) — ATR-only fallback for HIGH_VOLATILITY",
                lastVix.getCandleTime());
        return null;
    }

    private void storeInRedis(RegimeSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redis.opsForValue().set(REDIS_KEY, json, REDIS_TTL);
        } catch (Exception e) {
            log.error("[regime] Failed to store snapshot in Redis: {}", e.getMessage());
        }
    }
}
