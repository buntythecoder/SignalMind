package pl.piomin.signalmind.ingestion.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.piomin.signalmind.ingestion.domain.ConnectionState;
import pl.piomin.signalmind.ingestion.domain.MarketTick;
import pl.piomin.signalmind.integration.angelone.AngelOneConfig;
import pl.piomin.signalmind.stock.domain.Stock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Non-Spring WebSocket client for a single Angel One SmartAPI connection.
 *
 * <p>Instantiated directly by {@code DataIngestionService} — one instance per
 * logical connection (A = NIFTY50, B = everything else). Reconnect backoff is
 * managed externally by the service's {@code connectionLoop}.
 *
 * <p>Thread-safety: {@code connect()} and {@code awaitDisconnect()} are intended
 * to be called from a dedicated virtual thread per connection. The {@link ReentrantLock}
 * / {@link Condition} pair coordinates shutdown signalling from the {@link TickListener}.
 */
public class AngelOneWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(AngelOneWebSocketClient.class);

    private final String name;
    private final List<Stock> stocks;
    private final AngelOneConfig config;
    private final Consumer<MarketTick> tickHandler;
    private final AtomicReference<ConnectionState> state;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition disconnectedCondition = lock.newCondition();

    private volatile boolean open = false;
    private volatile WebSocket webSocket;

    public AngelOneWebSocketClient(
            String name,
            List<Stock> stocks,
            AngelOneConfig config,
            Consumer<MarketTick> tickHandler,
            AtomicReference<ConnectionState> state) {
        this.name = name;
        this.stocks = stocks;
        this.config = config;
        this.tickHandler = tickHandler;
        this.state = state;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Establish the WebSocket connection and send the subscribe message.
     * Blocks until the handshake succeeds or throws on timeout/error.
     */
    public void connect() throws Exception {
        open = false;

        HttpClient client = HttpClient.newHttpClient();
        webSocket = client.newWebSocketBuilder()
                // Placeholder headers — real implementation exchanges TOTP for a
                // session JWT and feed token via the Angel One login REST API.
                .header("Authorization", "Bearer " + config.apiKey())
                .header("x-api-key", config.apiKey())
                .header("x-client-code", config.clientId())
                .header("x-feed-token", config.totpSecret())
                .buildAsync(URI.create(config.feedUrl()), new TickListener())
                .get(30, TimeUnit.SECONDS);

        sendSubscribeMessage();

        lock.lock();
        try {
            open = true;
        } finally {
            lock.unlock();
        }

        log.info("[ws] conn{} connected to {} ({} stocks)", name, config.feedUrl(), stocks.size());
    }

    /**
     * Blocks the calling virtual thread until the connection is closed.
     * Returns normally when {@link #signalDisconnect()} fires.
     */
    public void awaitDisconnect() throws InterruptedException {
        lock.lock();
        try {
            while (open) {
                disconnectedCondition.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Initiates a clean WebSocket close handshake. No-op if already closed.
     */
    public void disconnect() {
        if (webSocket != null && !webSocket.isInputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join();
        }
    }

    public String getName() {
        return name;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sends an Angel One SmartAPI SnapQuote (mode 3) subscribe message for all
     * stocks in this connection that have a non-blank {@code angelToken}.
     *
     * <p>Action 1 = subscribe. ExchangeType 1 = NSE Cash.
     */
    private void sendSubscribeMessage() {
        String tokens = stocks.stream()
                .filter(s -> s.getAngelToken() != null && !s.getAngelToken().isBlank())
                .map(Stock::getAngelToken)
                .collect(Collectors.joining("\",\"", "\"", "\""));

        String msg = "{\"correlationID\":\"%s\",\"action\":1,\"params\":{\"mode\":3,\"tokenList\":[{\"exchangeType\":1,\"tokens\":[%s]}]}}"
                .formatted(UUID.randomUUID(), tokens);

        webSocket.sendText(msg, true).join();
        log.debug("[ws] conn{} subscribe sent ({} tokens)", name,
                stocks.stream().filter(s -> s.getAngelToken() != null && !s.getAngelToken().isBlank()).count());
    }

    private void signalDisconnect() {
        lock.lock();
        try {
            open = false;
            disconnectedCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // ── Inner WebSocket listener ──────────────────────────────────────────────

    private class TickListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
            log.info("[ws] conn{} open", name);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            // TODO: parse Angel One binary SnapQuote tick format.
            // Binary format documented at: https://smartapi.angelbroking.com/docs#Streaming
            // The parser will extract: token, ltp, open, high, low, close, volume.
            // For now: log at DEBUG and request the next frame.
            log.debug("[ws] conn{} binary tick {} bytes (parser TODO)", name, data.remaining());
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("[ws] conn{} closed: status={} reason={}", name, statusCode, reason);
            signalDisconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("[ws] conn{} error: {}", name, error.getMessage(), error);
            signalDisconnect();
        }
    }
}
