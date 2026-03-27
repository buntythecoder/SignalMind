package pl.piomin.signalmind.signal.domain;

/**
 * Lifecycle status of a {@link Signal} during the intraday trading session (SM-31).
 *
 * <p>State transitions:
 * <pre>
 *   GENERATED → TRIGGERED → TARGET_1_HIT
 *                         → TARGET_2_HIT
 *                         → STOP_HIT
 *             → EXPIRED        (validUntil passed without price entering entry zone)
 *   GENERATED/TRIGGERED → MARKET_CLOSE  (3:30 PM sweep)
 * </pre>
 *
 * <p>Values must match the {@code chk_signal_status} CHECK constraint added in V15.
 */
public enum SignalStatus {

    /** Initial state when signal is created by a detector. */
    GENERATED,

    /** Price entered the entry zone — trade is considered active. */
    TRIGGERED,

    /** T1 (primary profit target) reached. */
    TARGET_1_HIT,

    /** T2 (secondary profit target) reached, if applicable. */
    TARGET_2_HIT,

    /** Stop-loss level was hit. */
    STOP_HIT,

    /** {@code validUntil} passed without the signal ever reaching TRIGGERED. */
    EXPIRED,

    /** 3:30 PM end-of-day sweep — all remaining open positions closed for the session. */
    MARKET_CLOSE
}
