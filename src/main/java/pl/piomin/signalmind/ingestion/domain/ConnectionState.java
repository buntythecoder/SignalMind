package pl.piomin.signalmind.ingestion.domain;

/**
 * Lifecycle states for a single Angel One WebSocket connection.
 * Transitions managed by {@code DataIngestionService#connectionLoop}.
 */
public enum ConnectionState {

    /** No active connection and no reconnect in progress. */
    DISCONNECTED,

    /** Attempting to establish the WebSocket handshake. */
    CONNECTING,

    /** Handshake complete; receiving market ticks. */
    CONNECTED,

    /** Connection dropped; waiting for exponential-backoff delay before next attempt. */
    RECONNECTING
}
