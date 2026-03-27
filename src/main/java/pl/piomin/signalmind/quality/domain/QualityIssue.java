package pl.piomin.signalmind.quality.domain;

/**
 * Classification of a data-quality problem found in a {@link pl.piomin.signalmind.stock.domain.Candle}.
 */
public enum QualityIssue {

    /** A candle expected at a particular minute timestamp is absent from the database. */
    MISSING_CANDLE,

    /** High price is strictly less than Low price — impossible by definition. */
    HIGH_BELOW_LOW,

    /** High price is strictly less than Open price. */
    HIGH_BELOW_OPEN,

    /** High price is strictly less than Close price. */
    HIGH_BELOW_CLOSE,

    /** Low price is strictly greater than Open price. */
    LOW_ABOVE_OPEN,

    /** Low price is strictly greater than Close price. */
    LOW_ABOVE_CLOSE,

    /** At least one of O/H/L/C is null, zero, or negative. */
    ZERO_OR_NEGATIVE_PRICE,

    /** Volume is negative. */
    NEGATIVE_VOLUME,

    /** Candle timestamp falls outside the official NSE trading session window. */
    OUT_OF_SESSION
}
