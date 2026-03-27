package pl.piomin.signalmind.regime.domain;

/**
 * Market regime classification used by signal detectors to boost or suppress
 * confidence scores (SM-21, PRD Section 6.3).
 *
 * <p>The enum names match the DB CHECK constraint on {@code signals.regime}.
 */
public enum MarketRegime {
    TRENDING_UP,
    TRENDING_DOWN,
    SIDEWAYS,
    HIGH_VOLATILITY,
    CIRCUIT_HALT;

    /**
     * Confidence score modifier applied to any signal generated under this regime.
     * Positive = boost; negative = suppression.
     */
    public int confidenceModifier() {
        return switch (this) {
            case TRENDING_UP     ->  15;
            case TRENDING_DOWN   -> -15;
            case SIDEWAYS        ->  -5;
            case HIGH_VOLATILITY -> -20;
            case CIRCUIT_HALT    -> -100; // effectively blocks all signals
        };
    }
}
