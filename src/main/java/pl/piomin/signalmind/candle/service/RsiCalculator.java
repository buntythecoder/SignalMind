package pl.piomin.signalmind.candle.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Stateless utility for computing RSI(14) using Wilder's exponential-smoothing method.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Compute the first {@code PERIOD} price changes from the oldest end of the series.</li>
 *   <li>Average the gains and losses separately to get the <em>initial</em> average gain /
 *       average loss (simple average over the first window).</li>
 *   <li>For each subsequent close, apply Wilder smoothing:
 *       {@code avgGain = (prevAvgGain × (PERIOD−1) + currentGain) / PERIOD}</li>
 *   <li>RSI = 100 − 100 / (1 + RS)  where RS = avgGain / avgLoss</li>
 * </ol>
 *
 * <p>The input list must be ordered <em>oldest-first</em> and contain at least
 * {@code PERIOD + 1} entries; if not, {@code null} is returned.
 *
 * <p>SM-19
 */
public final class RsiCalculator {

    /** Wilder period — 14 candles per the canonical definition. */
    public static final int PERIOD = 14;

    private static final BigDecimal HUNDRED  = BigDecimal.valueOf(100);
    private static final BigDecimal PERIOD_BD = BigDecimal.valueOf(PERIOD);
    private static final BigDecimal PREV      = BigDecimal.valueOf(PERIOD - 1);
    private static final int SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private RsiCalculator() {
    }

    /**
     * Compute RSI(14) for the supplied price series.
     *
     * @param closes price series, oldest-first; minimum size is {@link #PERIOD} + 1
     * @return RSI value in [0, 100] rounded to 2 decimal places,
     *         or {@code null} when the list is too short
     */
    public static BigDecimal compute(List<BigDecimal> closes) {
        if (closes == null || closes.size() < PERIOD + 1) {
            return null;
        }

        // Step 1: initial average gain / loss from first PERIOD changes
        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;

        for (int i = 0; i < PERIOD; i++) {
            BigDecimal change = closes.get(i + 1).subtract(closes.get(i));
            if (change.compareTo(BigDecimal.ZERO) >= 0) {
                sumGain = sumGain.add(change);
            } else {
                sumLoss = sumLoss.add(change.negate());
            }
        }

        BigDecimal avgGain = sumGain.divide(PERIOD_BD, SCALE, RM);
        BigDecimal avgLoss = sumLoss.divide(PERIOD_BD, SCALE, RM);

        // Step 2: Wilder smoothing for each additional close beyond the seed window
        for (int i = PERIOD; i < closes.size() - 1; i++) {
            BigDecimal change = closes.get(i + 1).subtract(closes.get(i));
            BigDecimal gain = change.compareTo(BigDecimal.ZERO) >= 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.compareTo(BigDecimal.ZERO) < 0  ? change.negate() : BigDecimal.ZERO;

            avgGain = avgGain.multiply(PREV).add(gain).divide(PERIOD_BD, SCALE, RM);
            avgLoss = avgLoss.multiply(PREV).add(loss).divide(PERIOD_BD, SCALE, RM);
        }

        // Step 3: RSI formula
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return HUNDRED.setScale(2, RM);
        }
        BigDecimal rs  = avgGain.divide(avgLoss, SCALE, RM);
        BigDecimal rsi = HUNDRED.subtract(
                HUNDRED.divide(BigDecimal.ONE.add(rs), SCALE, RM));
        return rsi.setScale(2, RM);
    }
}
