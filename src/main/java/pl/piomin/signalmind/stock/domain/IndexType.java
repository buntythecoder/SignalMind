package pl.piomin.signalmind.stock.domain;

/**
 * The index/universe a stock belongs to.
 * A stock can belong to both NIFTY50 and BANKNIFTY.
 */
public enum IndexType {
    NIFTY50,
    BANKNIFTY,
    NIFTY_INDEX,   // NIFTY 50 index instrument (not a stock)
    INDIA_VIX      // India VIX instrument (not a stock)
}
