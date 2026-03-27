package pl.piomin.signalmind.regime.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Stateless utility that computes the Average True Range (ATR) using
 * Wilder's smoothing method (SM-21).
 *
 * <p>Minimum bars required: {@code period + 1} (one prior close is needed to
 * compute the first True Range value).
 */
public final class AtrCalculator {

    private AtrCalculator() {
        // utility class — no instantiation
    }

    /**
     * Computes ATR({@code period}) using Wilder's smoothing.
     *
     * @param bars   OHLC bars, oldest first
     * @param period smoothing period (typically 14)
     * @return ATR rounded to 4 decimal places, or {@code null} if there are
     *         fewer than {@code period + 1} bars
     */
    public static BigDecimal compute(List<OhlcBar> bars, int period) {
        if (bars == null || bars.size() < period + 1) {
            return null;
        }

        // Seed: simple average of the first `period` true-range values (bars 1..period)
        BigDecimal sumTr = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            sumTr = sumTr.add(trueRange(bars.get(i), bars.get(i - 1).close()));
        }
        BigDecimal atr = sumTr.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        // Wilder smoothing: ATR(n) = (ATR(n-1) * (period-1) + TR(n)) / period
        for (int i = period + 1; i < bars.size(); i++) {
            BigDecimal tr = trueRange(bars.get(i), bars.get(i - 1).close());
            atr = atr.multiply(BigDecimal.valueOf(period - 1))
                    .add(tr)
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        }

        return atr.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Computes the True Range for a single bar.
     *
     * <p>True Range = max(High-Low, |High-PrevClose|, |Low-PrevClose|)
     *
     * @param bar       current OHLC bar
     * @param prevClose previous bar's closing price
     * @return true range value
     */
    public static BigDecimal trueRange(OhlcBar bar, BigDecimal prevClose) {
        BigDecimal hl  = bar.high().subtract(bar.low()).abs();
        BigDecimal hpc = bar.high().subtract(prevClose).abs();
        BigDecimal lpc = bar.low().subtract(prevClose).abs();
        return hl.max(hpc).max(lpc);
    }
}
