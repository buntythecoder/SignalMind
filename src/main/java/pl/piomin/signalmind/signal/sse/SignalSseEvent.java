package pl.piomin.signalmind.signal.sse;

/**
 * Immutable event envelope sent over the SSE stream (SM-30).
 *
 * @param eventId   monotonically-increasing sequence number used by the browser
 *                  to populate the {@code Last-Event-ID} header on reconnect
 * @param eventType the SSE {@code event:} field value; one of
 *                  {@code SIGNAL_GENERATED} or {@code SIGNAL_STATUS_CHANGED}
 * @param payload   arbitrary JSON-serialisable payload for the event
 */
public record SignalSseEvent(
        long eventId,
        String eventType,
        Object payload
) {}
