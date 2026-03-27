package pl.piomin.signalmind.ingestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.ingestion.domain.ConnectionState;
import pl.piomin.signalmind.ingestion.domain.MarketTick;
import pl.piomin.signalmind.ingestion.websocket.AngelOneWebSocketClient;
import pl.piomin.signalmind.integration.angelone.AngelOneConfig;
import pl.piomin.signalmind.integration.telegram.TelegramAlertService;
import pl.piomin.signalmind.market.service.MarketDayStateService;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;
import pl.piomin.signalmind.stock.repository.StockRepository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dual WebSocket connection manager for Angel One SmartAPI market data.
 *
 * <p>Connection A streams NIFTY50 stocks (~50 symbols).
 * Connection B streams BANKNIFTY, NIFTY_INDEX, and INDIA_VIX instruments (~12 symbols).
 *
 * <p>Each connection runs in its own virtual thread with exponential-backoff reconnect.
 * Incoming ticks are fanned out to all registered {@link TickBuffer} implementations.
 *
 * <p>Only activated when {@code angelone.ingestion.enabled=true} is set, so the bean
 * is absent in the {@code test} profile and in standard context-load tests.
 */
@Service
@ConditionalOnProperty(name = "angelone.ingestion.enabled", havingValue = "true", matchIfMissing = false)
public class DataIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionService.class);

    /** SM-20: send Telegram alert after this many consecutive reconnect failures. */
    private static final int ALERT_THRESHOLD = 3;

    /** SM-20: stop the connection loop after this many consecutive reconnect failures. */
    private static final int MAX_FAILURES = 5;

    private final AngelOneConfig config;
    private final StockRepository stockRepository;
    private final List<TickBuffer> tickBuffers;
    private final MarketDayStateService marketDayState;
    private final TelegramAlertService telegramAlertService;

    private final AtomicReference<ConnectionState> stateA = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicReference<ConnectionState> stateB = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicReference<Instant> lastTickAt = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile AngelOneWebSocketClient connA;
    private volatile AngelOneWebSocketClient connB;

    public DataIngestionService(
            AngelOneConfig config,
            StockRepository stockRepository,
            List<TickBuffer> tickBuffers,
            MarketDayStateService marketDayState,
            TelegramAlertService telegramAlertService) {
        this.config = config;
        this.stockRepository = stockRepository;
        this.tickBuffers = tickBuffers;
        this.marketDayState = marketDayState;
        this.telegramAlertService = telegramAlertService;
    }

    // ── ApplicationRunner ─────────────────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        if (!marketDayState.isTodayTradingDay()) {
            log.info("[ingestion] today is not a trading day — WebSocket connections skipped");
            return;
        }
        start();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        running.set(true);

        List<Stock> all = stockRepository.findByActiveTrueOrderBySymbolAsc();
        List<Stock> forA = selectConnectionAStocks(all);
        List<Stock> forB = selectConnectionBStocks(all);

        log.info("[ingestion] starting — connA: {} NIFTY50 stocks, connB: {} other symbols",
                forA.size(), forB.size());

        connA = new AngelOneWebSocketClient("A", forA, config, this::onTick, stateA);
        connB = new AngelOneWebSocketClient("B", forB, config, this::onTick, stateB);

        Thread.ofVirtual().name("ws-connA").start(() -> connectionLoop(connA, stateA, "A"));
        Thread.ofVirtual().name("ws-connB").start(() -> connectionLoop(connB, stateB, "B"));
    }

    public void stop() {
        running.set(false);
        stateA.set(ConnectionState.DISCONNECTED);
        stateB.set(ConnectionState.DISCONNECTED);

        if (connA != null) {
            connA.disconnect();
        }
        if (connB != null) {
            connB.disconnect();
        }

        log.info("[ingestion] stop requested — connections closing");
    }

    // ── Public state accessors ────────────────────────────────────────────────

    public ConnectionState getStateA() {
        return stateA.get();
    }

    public ConnectionState getStateB() {
        return stateB.get();
    }

    public Instant getLastTickAt() {
        return lastTickAt.get();
    }

    // ── Stock partitioning ────────────────────────────────────────────────────

    /**
     * Returns stocks belonging to Connection A: all NIFTY50 stocks.
     * Public static for unit testing without Spring context.
     */
    public static List<Stock> selectConnectionAStocks(List<Stock> all) {
        return all.stream()
                .filter(s -> s.getIndexType() == IndexType.NIFTY50)
                .toList();
    }

    /**
     * Returns stocks belonging to Connection B: BANKNIFTY, NIFTY_INDEX, INDIA_VIX.
     * Public static for unit testing without Spring context.
     */
    public static List<Stock> selectConnectionBStocks(List<Stock> all) {
        return all.stream()
                .filter(s -> s.getIndexType() != IndexType.NIFTY50)
                .toList();
    }

    // ── Private implementation ────────────────────────────────────────────────

    private void connectionLoop(
            AngelOneWebSocketClient client,
            AtomicReference<ConnectionState> state,
            String connName) {

        int attempts = 0;
        int consecutiveFailures = 0;

        while (running.get()) {
            try {
                state.set(ConnectionState.CONNECTING);
                client.connect();
                state.set(ConnectionState.CONNECTED);
                log.info("[ingestion] conn{} CONNECTED", connName);

                // Reset counters on successful connection (SM-20)
                attempts = 0;
                consecutiveFailures = 0;

                client.awaitDisconnect();

                if (!running.get()) {
                    break;
                }

                // Clean disconnect followed by reconnect counts as a failure (SM-20)
                consecutiveFailures++;
                state.set(ConnectionState.RECONNECTING);
                long delayMs = backoffDelayMs(attempts++);
                log.warn("[ingestion] conn{} disconnected, reconnecting in {}ms (attempt {})",
                        connName, delayMs, attempts);

                notifyIfThresholdReached(connName, consecutiveFailures, "unexpected disconnect");

                if (consecutiveFailures >= MAX_FAILURES) {
                    log.error("[ingestion] conn{} reached {} consecutive failures — stopping loop",
                            connName, consecutiveFailures);
                    break;
                }

                Thread.sleep(delayMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                consecutiveFailures++;
                log.error("[ingestion] conn{} failed: {}", connName, e.getMessage(), e);
                state.set(ConnectionState.RECONNECTING);

                notifyIfThresholdReached(connName, consecutiveFailures, e.getMessage());

                if (consecutiveFailures >= MAX_FAILURES) {
                    log.error("[ingestion] conn{} reached {} consecutive failures — stopping loop",
                            connName, consecutiveFailures);
                    break;
                }

                try {
                    Thread.sleep(backoffDelayMs(attempts++));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        state.set(ConnectionState.DISCONNECTED);
        log.info("[ingestion] conn{} loop exited", connName);
    }

    /**
     * Send a Telegram alert exactly once when consecutive failures hit the alert threshold (SM-20).
     * Fires only at {@code consecutiveFailures == ALERT_THRESHOLD}, not on every subsequent failure,
     * to avoid flooding the channel.
     */
    private void notifyIfThresholdReached(String connName, int consecutiveFailures, String lastError) {
        if (consecutiveFailures == ALERT_THRESHOLD) {
            String msg = String.format(
                    "⚠️ SignalMind conn%s: %d consecutive reconnect failures. Last error: %s",
                    connName, consecutiveFailures, lastError);
            telegramAlertService.sendAlert(msg);
        }
    }

    /**
     * Exponential backoff: 3s, 6s, 12s, 24s, 48s, then capped at 60s.
     */
    private long backoffDelayMs(int attempt) {
        return Math.min(3_000L * (1L << Math.min(attempt, 5)), 60_000L);
    }

    private void onTick(MarketTick tick) {
        lastTickAt.set(Instant.now());
        for (TickBuffer buffer : tickBuffers) {
            if (buffer.supports(tick.symbol())) {
                buffer.publish(tick);
            }
        }
    }
}
