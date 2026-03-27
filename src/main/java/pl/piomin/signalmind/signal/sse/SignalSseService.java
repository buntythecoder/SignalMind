package pl.piomin.signalmind.signal.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.piomin.signalmind.signal.domain.Signal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages SSE emitter registrations and broadcasts signal events to all
 * connected dashboard clients (SM-30).
 *
 * <h2>Missed-event replay</h2>
 * The last {@value #MAX_STORED_EVENTS} events are kept in an in-memory deque.
 * When a client reconnects and supplies a {@code Last-Event-ID} header the
 * service replays every event whose {@code eventId} is strictly greater than
 * the supplied value, allowing seamless reconnection without gaps.
 *
 * <h2>Thread safety</h2>
 * {@code emitters} uses {@link CopyOnWriteArrayList} so iteration during
 * broadcast is safe while concurrent registrations/removals occur.
 * {@code recentEvents} is guarded by {@code synchronized(recentEvents)}.
 */
@Service
public class SignalSseService {

    private static final Logger log = LoggerFactory.getLogger(SignalSseService.class);

    static final int MAX_STORED_EVENTS = 200;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong eventIdCounter = new AtomicLong(0);

    /** Bounded ring-buffer of recent events for missed-event replay. */
    private final Deque<SignalSseEvent> recentEvents = new ArrayDeque<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Registers a new SSE client connection and replays any events missed since
     * {@code lastEventId} (inclusive of the boundary: events strictly after that ID).
     *
     * @param lastEventId the value of the client's {@code Last-Event-ID} header,
     *                    or {@code null} for a fresh connection
     * @return the configured {@link SseEmitter} to be returned from the controller
     */
    public SseEmitter register(Long lastEventId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = no timeout

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("[sse] Emitter completed — {} active connections", emitters.size());
        });
        emitter.onError(ex -> {
            emitters.remove(emitter);
            log.debug("[sse] Emitter error ({}) — {} active connections",
                    ex.getMessage(), emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("[sse] Emitter timeout — {} active connections", emitters.size());
        });

        // Replay missed events before adding to the live list so that any
        // events generated during replay do not reach this emitter twice.
        if (lastEventId != null) {
            replayMissedEvents(emitter, lastEventId);
        }

        emitters.add(emitter);
        log.info("[sse] Client registered (lastEventId={}) — {} active connections",
                lastEventId, emitters.size());
        return emitter;
    }

    /**
     * Broadcasts a new signal to all connected SSE clients.
     *
     * @param signal      the persisted signal entity
     * @param stockSymbol the ticker symbol for the signal's stock
     */
    public void broadcastSignal(Signal signal, String stockSymbol) {
        long id = eventIdCounter.incrementAndGet();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signalId", signal.getId());
        payload.put("stockSymbol", stockSymbol);
        payload.put("signalType", signal.getSignalType() != null ? signal.getSignalType().name() : null);
        payload.put("direction", signal.getDirection() != null ? signal.getDirection().name() : null);
        payload.put("entryPrice", signal.getEntryPrice());
        payload.put("confidence", signal.getConfidence());
        payload.put("generatedAt", signal.getGeneratedAt() != null ? signal.getGeneratedAt().toString() : null);

        SignalSseEvent event = new SignalSseEvent(id, "SIGNAL_GENERATED", payload);
        storeEvent(event);
        broadcast(event);
    }

    /**
     * Broadcasts a signal-status change to all connected SSE clients.
     *
     * @param signalId  the ID of the affected signal
     * @param newStatus the new status string (e.g. {@code "DISPATCHED"}, {@code "EXPIRED"})
     */
    public void broadcastStatusChange(Long signalId, String newStatus) {
        long id = eventIdCounter.incrementAndGet();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signalId", signalId);
        payload.put("newStatus", newStatus);

        SignalSseEvent event = new SignalSseEvent(id, "SIGNAL_STATUS_CHANGED", payload);
        storeEvent(event);
        broadcast(event);
    }

    // ── Package-visible for tests ──────────────────────────────────────────────

    /** Returns the live emitter list (for testing — do not hold a reference across threads). */
    List<SseEmitter> getEmitters() {
        return emitters;
    }

    /** Returns a snapshot of stored recent events (for testing). */
    List<SignalSseEvent> getRecentEvents() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Iterates all stored events and sends those with an {@code eventId} strictly
     * greater than {@code lastEventId} to the newly-reconnecting emitter.
     */
    private void replayMissedEvents(SseEmitter emitter, long lastEventId) {
        List<SignalSseEvent> toReplay;
        synchronized (recentEvents) {
            toReplay = recentEvents.stream()
                    .filter(e -> e.eventId() > lastEventId)
                    .toList();
        }
        log.debug("[sse] Replaying {} missed events (lastEventId={})", toReplay.size(), lastEventId);
        for (SignalSseEvent event : toReplay) {
            send(emitter, event);
        }
    }

    /** Delivers {@code event} to every active emitter, removing dead ones. */
    private void broadcast(SignalSseEvent event) {
        for (SseEmitter emitter : emitters) {
            send(emitter, event);
        }
    }

    /**
     * Sends a single event to one emitter.  Any write failure is treated as a
     * dead connection: the emitter is removed and completed.
     */
    private void send(SseEmitter emitter, SignalSseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.eventId()))
                    .name(event.eventType())
                    .data(event.payload(), MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            emitters.remove(emitter);
            emitter.complete();
            log.debug("[sse] Removed dead emitter after send failure: {}", ex.getMessage());
        }
    }

    /**
     * Stores the event in the bounded ring-buffer, evicting the oldest entry
     * when the buffer exceeds {@value #MAX_STORED_EVENTS} items.
     */
    private void storeEvent(SignalSseEvent event) {
        synchronized (recentEvents) {
            recentEvents.addLast(event);
            while (recentEvents.size() > MAX_STORED_EVENTS) {
                recentEvents.removeFirst();
            }
        }
    }
}
