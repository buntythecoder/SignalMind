package pl.piomin.signalmind.signal.domain;

/**
 * Trade direction of a generated signal.
 *
 * <p>Values must match the {@code chk_signals_direction} CHECK constraint:
 * {@code direction IN ('LONG','SHORT')}.
 */
public enum SignalDirection {
    LONG,
    SHORT
}
