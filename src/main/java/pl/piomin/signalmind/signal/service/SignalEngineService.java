package pl.piomin.signalmind.signal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.integration.telegram.TelegramAlertService;
import pl.piomin.signalmind.regime.service.MarketRegimeService;
import pl.piomin.signalmind.signal.detector.SignalDetector;
import pl.piomin.signalmind.signal.domain.Signal;
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

/**
 * Orchestrates all registered {@link SignalDetector} implementations during the
 * Opening-Range Breakout window (SM-22).
 *
 * <h2>Scheduling</h2>
 * Runs at second=30 of every minute from 09:30 to 11:35 IST, Monday–Friday.
 * The 30-second offset gives the candle assembler time to close and persist the
 * just-completed minute before detection runs.
 *
 * <h2>Per-run logic</h2>
 * <ol>
 *   <li>Resolve the current market regime from Redis (falls back to SIDEWAYS).</li>
 *   <li>For each active stock × detector, skip if a signal was already generated this session.</li>
 *   <li>Load today's candles, reverse to oldest-first, build volume-baseline map.</li>
 *   <li>Call {@link SignalDetector#detect}; persist and optionally dispatch the result.</li>
 *   <li>Move to next stock after the first signal (one signal per stock per run).</li>
 * </ol>
 */
@Service
public class SignalEngineService {

    private static final Logger log = LoggerFactory.getLogger(SignalEngineService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime ENGINE_START = LocalTime.of(9, 30);
    private static final LocalTime ENGINE_END   = LocalTime.of(11, 35);

    /** Minimum confidence for Telegram dispatch. */
    private static final int DISPATCH_CONFIDENCE_GATE = 60;

    private final List<SignalDetector> detectors;
    private final StockRepository stockRepository;
    private final CandleRepository candleRepository;
    private final VolumeBaselineRepository volumeBaselineRepository;
    private final SignalRepository signalRepository;
    private final TelegramAlertService telegram;
    private final Optional<MarketRegimeService> regimeService;

    public SignalEngineService(List<SignalDetector> detectors,
                               StockRepository stockRepository,
                               CandleRepository candleRepository,
                               VolumeBaselineRepository volumeBaselineRepository,
                               SignalRepository signalRepository,
                               TelegramAlertService telegram,
                               Optional<MarketRegimeService> regimeService) {
        this.detectors = detectors;
        this.stockRepository = stockRepository;
        this.candleRepository = candleRepository;
        this.volumeBaselineRepository = volumeBaselineRepository;
        this.signalRepository = signalRepository;
        this.telegram = telegram;
        this.regimeService = regimeService;
    }

    // ── Scheduled run ─────────────────────────────────────────────────────────

    /**
     * Main engine tick — runs at :30 of every minute during the ORB window.
     * Returns immediately if the current IST time is outside [ENGINE_START, ENGINE_END].
     */
    @Scheduled(cron = "30 * * * * MON-FRI", zone = "Asia/Kolkata")
    public void runSignalEngine() {
        LocalTime now = LocalTime.now(IST);
        if (now.isBefore(ENGINE_START) || now.isAfter(ENGINE_END)) {
            return;
        }

        LocalDate today = LocalDate.now(IST);
        Instant sessionStart = today.atTime(9, 15).atZone(IST).toInstant();
        Instant sessionEnd   = today.atTime(11, 35).atZone(IST).toInstant();

        String regime = regimeService
                .flatMap(MarketRegimeService::currentRegime)
                .map(snap -> snap.regime().name())
                .orElse("SIDEWAYS");

        List<Stock> stocks = stockRepository.findAllByActiveTrue();
        int signalsGenerated = 0;

        for (Stock stock : stocks) {
            boolean stockSignalGenerated = false;

            for (SignalDetector detector : detectors) {

                // One signal per detector type per stock per session
                if (signalRepository.existsByStockAndSignalTypeAndGeneratedAtBetween(
                        stock, detector.signalType(), sessionStart, sessionEnd)) {
                    continue;
                }

                // Load today's candles (repository returns newest-first; reverse for oldest-first)
                List<Candle> rawCandles = candleRepository
                        .findByStockAndTimeRange(stock.getId(), sessionStart, sessionEnd);
                if (rawCandles.isEmpty()) {
                    continue;
                }

                List<Candle> candles = new ArrayList<>(rawCandles);
                Collections.reverse(candles);

                // Build volume baselines map lazily: slot time → avg volume
                Map<LocalTime, Long> baselines = buildBaselines(stock, candles);

                Optional<Signal> result = detector.detect(stock, candles, baselines, regime);
                result.ifPresent(signal -> {
                    signalRepository.save(signal);
                    log.info("[signal-engine] {} {} {} signal for {} entry={} confidence={} valid_until={}",
                            signal.getSignalType(), signal.getDirection(), stock.getSymbol(),
                            signal.getEntryPrice(), signal.getConfidence(), signal.getValidUntil());

                    if (signal.getConfidence() >= DISPATCH_CONFIDENCE_GATE) {
                        telegram.sendAlert(formatAlert(signal, stock));
                    }
                });

                if (result.isPresent()) {
                    signalsGenerated++;
                    stockSignalGenerated = true;
                    break; // one signal per stock per run
                }
            }

            if (stockSignalGenerated) {
                // Move to next stock — already broke out of detector loop
                continue;
            }
        }

        log.info("[signal-engine] Run complete: {} new signals generated at {}", signalsGenerated, now);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    /**
     * Formats a Telegram alert message for a generated signal.
     * Uses plain text with emoji for readability in Telegram.
     */
    private String formatAlert(Signal signal, Stock stock) {
        return String.format(
                "📊 %s %s | %s | Entry: %.2f | SL: %.2f | T1: %.2f | T2: %.2f | Conf: %d%%",
                signal.getSignalType(), signal.getDirection(), stock.getSymbol(),
                signal.getEntryPrice(), signal.getStopLoss(),
                signal.getTargetPrice(), signal.getTarget2(),
                signal.getConfidence());
    }
}
