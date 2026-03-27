package pl.piomin.signalmind.signal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.integration.telegram.TelegramAlertFormatter;
import pl.piomin.signalmind.integration.telegram.TelegramAlertService;
import pl.piomin.signalmind.regime.service.MarketRegimeService;
import pl.piomin.signalmind.signal.detector.SignalDetector;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.signal.repository.SignalRepository;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.CandleRepository;
import pl.piomin.signalmind.stock.repository.StockRepository;
import pl.piomin.signalmind.stock.repository.VolumeBaselineRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates all registered {@link SignalDetector} implementations during
 * the Opening-Range Breakout window (SM-22).
 *
 * <h2>Scheduling</h2>
 * Runs at second=30 of every minute from 09:15 to 15:35 IST, Monday–Friday.
 * The 30-second offset gives the candle assembler time to close and persist the
 * just-completed minute before detection runs.
 *
 * <h2>Per-run logic</h2>
 * <ol>
 *   <li>Resolve the current market regime from Redis (falls back to SIDEWAYS).</li>
 *   <li>For each active stock × detector, apply SM-27 guardrails:
 *       feature-flag check, Redis deduplication, per-detector daily cap,
 *       and max-3-signals-per-stock guardrail.</li>
 *   <li>Load today's candles, reverse to oldest-first, build volume-baseline map.</li>
 *   <li>Call {@link SignalDetector#detect}; apply SM-26 confidence scoring,
 *       persist and optionally dispatch the result.</li>
 *   <li>Dispatch gate (SM-27): skip Telegram if daily dispatch budget (25) is
 *       exhausted or the per-minute rate limit (5/min) is reached.</li>
 *   <li>Move to next stock after the first signal (one signal per stock per run).</li>
 * </ol>
 */
@Service
public class SignalEngineService {

    private static final Logger log = LoggerFactory.getLogger(SignalEngineService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime ENGINE_START = LocalTime.of(9, 15);
    private static final LocalTime ENGINE_END   = LocalTime.of(15, 35);

    /** Minimum confidence for Telegram dispatch (SM-26). */
    private static final int DISPATCH_CONFIDENCE_GATE = 60;

    /** SM-27 guardrails. */
    private static final int MAX_SIGNALS_PER_STOCK_PER_DAY = 3;
    private static final int MAX_DISPATCHED_PER_DAY        = 25;
    private static final int MAX_DISPATCHED_PER_MINUTE     = 5;

    private final List<SignalDetector> detectors;
    private final StockRepository stockRepository;
    private final CandleRepository candleRepository;
    private final VolumeBaselineRepository volumeBaselineRepository;
    private final SignalRepository signalRepository;
    private final TelegramAlertService telegram;
    private final Optional<MarketRegimeService> regimeService;
    private final ConfidenceScoringService scoringService;
    private final Optional<SignalTypeConfigService> configService;
    private final Optional<SignalDeduplicationService> deduplicationService;
    private final TelegramAlertFormatter formatter;

    /**
     * Per-minute dispatch counter (SM-27: max 5 dispatches per minute).
     * Reset at the start of each engine tick by {@link #refreshMinuteCounter}.
     */
    private final AtomicInteger minuteDispatchCount = new AtomicInteger(0);
    private volatile int currentMinuteOfDay = -1;

    public SignalEngineService(List<SignalDetector> detectors,
                               StockRepository stockRepository,
                               CandleRepository candleRepository,
                               VolumeBaselineRepository volumeBaselineRepository,
                               SignalRepository signalRepository,
                               TelegramAlertService telegram,
                               Optional<MarketRegimeService> regimeService,
                               ConfidenceScoringService scoringService,
                               Optional<SignalTypeConfigService> configService,
                               Optional<SignalDeduplicationService> deduplicationService,
                               TelegramAlertFormatter formatter) {
        this.detectors             = detectors;
        this.stockRepository       = stockRepository;
        this.candleRepository      = candleRepository;
        this.volumeBaselineRepository = volumeBaselineRepository;
        this.signalRepository      = signalRepository;
        this.telegram              = telegram;
        this.regimeService         = regimeService;
        this.scoringService        = scoringService;
        this.configService         = configService;
        this.deduplicationService  = deduplicationService;
        this.formatter             = formatter;
    }

    // ── Scheduled run ─────────────────────────────────────────────────────────

    /**
     * Main engine tick — runs at :30 of every minute during the trading session.
     * Returns immediately if the current IST time is outside [ENGINE_START, ENGINE_END].
     */
    @Scheduled(cron = "30 * * * * MON-FRI", zone = "Asia/Kolkata")
    public void runSignalEngine() {
        LocalTime now = LocalTime.now(IST);
        if (now.isBefore(ENGINE_START) || now.isAfter(ENGINE_END)) {
            return;
        }

        refreshMinuteCounter(now);

        LocalDate today = LocalDate.now(IST);
        Instant sessionStart = today.atTime(9, 15).atZone(IST).toInstant();
        Instant sessionEnd   = today.atTime(15, 35).atZone(IST).toInstant();

        String regime = regimeService
                .flatMap(MarketRegimeService::currentRegime)
                .map(snap -> snap.regime().name())
                .orElse("SIDEWAYS");

        // SM-27: check daily dispatch budget once per run
        long dispatchedToday = signalRepository
                .countByDispatchedTrueAndGeneratedAtBetween(sessionStart, sessionEnd);
        boolean dailyBudgetExhausted = dispatchedToday >= MAX_DISPATCHED_PER_DAY;
        if (dailyBudgetExhausted) {
            log.debug("[signal-engine] Daily dispatch budget reached ({}/{}), signals will be saved but not dispatched",
                    dispatchedToday, MAX_DISPATCHED_PER_DAY);
        }

        List<Stock> stocks = stockRepository.findAllByActiveTrue();
        int signalsGenerated = 0;

        for (Stock stock : stocks) {
            boolean stockSignalGenerated = false;

            // SM-27: max 3 signals per stock per day across all types
            long stockSignalsToday = signalRepository
                    .countByStockAndGeneratedAtBetween(stock, sessionStart, sessionEnd);
            if (stockSignalsToday >= MAX_SIGNALS_PER_STOCK_PER_DAY) {
                continue;
            }

            for (SignalDetector detector : detectors) {

                // SM-27: feature flag — skip disabled signal types (fail-open when service absent)
                if (configService.map(s -> !s.isEnabled(detector.signalType())).orElse(false)) {
                    continue;
                }

                // SM-27: Redis deduplication — 30-min cooldown per stock/type
                if (deduplicationService.map(d -> d.isDuplicate(stock, detector.signalType()))
                        .orElse(false)) {
                    continue;
                }

                // Enforce per-detector daily signal cap (1 by default; VWAP detectors share a cap of 3)
                long existingCount = signalRepository.countByStockAndSignalTypeInAndGeneratedAtBetween(
                        stock, detector.countedTypes(), sessionStart, sessionEnd);
                if (existingCount >= detector.maxSignalsPerDay()) {
                    // The dedup key was set above — clear it to avoid wasting the slot
                    deduplicationService.ifPresent(d -> d.clearEntry(stock, detector.signalType()));
                    continue;
                }

                // Load today's candles (repository returns newest-first; reverse for oldest-first)
                List<Candle> rawCandles = candleRepository
                        .findByStockAndTimeRange(stock.getId(), sessionStart, sessionEnd);
                if (rawCandles.isEmpty()) {
                    deduplicationService.ifPresent(d -> d.clearEntry(stock, detector.signalType()));
                    continue;
                }

                List<Candle> candles = new ArrayList<>(rawCandles);
                Collections.reverse(candles);

                // Build volume baselines map lazily: slot time → avg volume
                Map<LocalTime, Long> baselines = buildBaselines(stock, candles);

                Optional<Signal> result = detector.detect(stock, candles, baselines, regime);

                if (result.isEmpty()) {
                    // Detection failed — clear the dedup slot so the detector can retry next tick
                    deduplicationService.ifPresent(d -> d.clearEntry(stock, detector.signalType()));
                    continue;
                }

                Signal signal = result.get();

                // SM-26: apply 6-factor confidence scoring before persistence
                Instant triggerTime = signal.getGeneratedAt();
                Candle triggerCandle = candles.stream()
                        .filter(c -> c.getCandleTime().equals(triggerTime))
                        .findFirst()
                        .orElse(candles.get(0));
                long volume = triggerCandle.getVolume();
                LocalTime slot = triggerTime.atZone(IST).toLocalTime();
                Long baseline = baselines.get(slot);
                scoringService.score(signal, volume, baseline);

                signalRepository.save(signal);
                signalsGenerated++;
                stockSignalGenerated = true;

                log.info("[signal-engine] {} {} {} signal for {} entry={} confidence={} " +
                                "(base={} vol={} tod={} reg={} wr={} conf={}) valid_until={}",
                        signal.getSignalType(), signal.getDirection(), stock.getSymbol(),
                        signal.getEntryPrice(), signal.getConfidence(),
                        signal.getScoreBase(), signal.getScoreVolume(),
                        signal.getScoreTimeOfDay(), signal.getScoreRegime(),
                        signal.getScoreWinRate(), signal.getScoreConfluence(),
                        signal.getValidUntil());

                // SM-27: dispatch gates — confidence, daily budget, per-minute rate limit
                if (signal.getConfidence() >= DISPATCH_CONFIDENCE_GATE
                        && !dailyBudgetExhausted
                        && minuteDispatchCount.get() < MAX_DISPATCHED_PER_MINUTE) {
                    long stockCountToday = signalRepository
                            .countByStockAndGeneratedAtBetween(stock, sessionStart, sessionEnd);
                    TelegramAlertFormatter.FormattedAlert formatted =
                            formatter.format(signal, stock, stockCountToday);
                    telegram.sendAlertWithKeyboard(formatted.text(), formatted.replyMarkupJson());
                    signal.markDispatched(Instant.now());
                    signalRepository.save(signal); // persist dispatch timestamp
                    minuteDispatchCount.incrementAndGet();
                }

                break; // one signal per stock per run
            }

            if (stockSignalGenerated) {
                continue;
            }
        }

        log.info("[signal-engine] Run complete: {} new signals generated at {}", signalsGenerated, now);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resets the per-minute dispatch counter when the minute rolls over.
     */
    private void refreshMinuteCounter(LocalTime now) {
        int minute = now.getHour() * 60 + now.getMinute();
        if (minute != currentMinuteOfDay) {
            currentMinuteOfDay = minute;
            minuteDispatchCount.set(0);
        }
    }

    /**
     * Builds a map of IST slot time → historical average volume by querying the
     * volume_baselines table once per unique slot present in the candle list.
     */
    private Map<LocalTime, Long> buildBaselines(Stock stock, List<Candle> candles) {
        Map<LocalTime, Long> baselines = new HashMap<>();
        for (Candle c : candles) {
            LocalTime slot = c.getCandleTime().atZone(IST).toLocalTime();
            if (!baselines.containsKey(slot)) {
                volumeBaselineRepository.findByStockAndSlotTime(stock, slot)
                        .ifPresent(vb -> baselines.put(slot, vb.getAvgVolume()));
            }
        }
        return baselines;
    }


}
