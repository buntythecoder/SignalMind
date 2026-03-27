package pl.piomin.signalmind.regime.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Stateless utility that computes the Exponential Moving Average (EMA)
 * for a price series (SM-21).
 *
 * <p>Algorithm: seeds the EMA with the SMA of the first {@code period} values,
 * then applies the standard EMA formula for the remaining prices.
 */
public final class EmaCalculator {

    private EmaCalculator() {
        // utility class — no instantiation
    }

    /**
     * Computes EMA({@code period}) on the given price series (oldest first).
     *
     * @param prices price series, oldest first
     * @param period smoothing period (must be >= 1)
     * @return EMA value rounded to 4 decimal places, or {@code null} if there
     *         are fewer than {@code period} values
     */
    public static BigDecimal compute(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period) {
            return null;
        }

        // Multiplier: k = 2 / (period + 1)
        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k);

        // Seed: SMA of the first `period` values
        BigDecimal ema = prices.subList(0, period)
                .stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        // Apply EMA formula for remaining prices
        for (int i = period; i < prices.size(); i++) {
            ema = prices.get(i).multiply(k).add(ema.multiply(oneMinusK));
        }

        return ema.setScale(4, RoundingMode.HALF_UP);
    }
}
