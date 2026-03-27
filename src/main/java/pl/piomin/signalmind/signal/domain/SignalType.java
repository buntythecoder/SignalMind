package pl.piomin.signalmind.signal.domain;

/**
 * Discriminator for the type of trading signal generated.
 *
 * <p>Values must match the {@code chk_signals_type} CHECK constraint defined
 * in V4 and referenced throughout the signals table.
 */
public enum SignalType {
    ORB,
    VWAP_BREAKOUT,
    VWAP_BREAKDOWN,
    RSI_REVERSAL,
    GAP_FILL_LONG,
    GAP_FILL_SHORT
}
