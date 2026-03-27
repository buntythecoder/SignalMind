package pl.piomin.signalmind.regime.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Stateless utility that computes a DX (Directional Index) value using
 * Wilder's smoothed Directional Movement approach (SM-21).
 *
 * <p>This is a single-pass approximation: it produces the DX value for the
 * final bar rather than a full smoothed ADX series. For regime detection the
 * threshold comparisons (25 for trending, 20 for sideways) work correctly
 * with this approximation.
 *
 * <p>Minimum bars required: {@code 2 * period + 1}.
 */
public final class AdxCalculator {

    private static final BigDecimal BD_100 = BigDecimal.valueOf(100);

    private AdxCalculator() {
        // utility class — no instantiation
    }

    /**
     * Computes DX({@code period}) using Wilder's smoothed DM and TR.
     *
     * @param bars   OHLC bars, oldest first
     * @param period smoothing period (typically 14)
     * @return DX value rounded to 2 decimal places, or {@code null} if there
     *         are fewer than {@code 2 * period + 1} bars
     */
    public static BigDecimal compute(List<OhlcBar> bars, int period) {
        if (bars == null || bars.size() < 2 * period + 1) {
            return null;
        }

        BigDecimal[] smoothed = wilderSmoothedDMAndTR(bars, period);
        BigDecimal smoothedPlusDM  = smoothed[0];
        BigDecimal smoothedMinusDM = smoothed[1];
        BigDecimal smoothedTR      = smoothed[2];

        if (smoothedTR.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // +DI and -DI
        BigDecimal pDI  = smoothedPlusDM.multiply(BD_100)
                .divide(smoothedTR, 8, RoundingMode.HALF_UP);
        BigDecimal mDI  = smoothedMinusDM.multiply(BD_100)
                .divide(smoothedTR, 8, RoundingMode.HALF_UP);

        // DX = |+DI - -DI| / (+DI + -DI) * 100
        BigDecimal diff = pDI.subtract(mDI).abs();
        BigDecimal sum  = pDI.add(mDI);

        BigDecimal dx = sum.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : diff.multiply(BD_100).divide(sum, 8, RoundingMode.HALF_UP);

        return dx.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Applies Wilder's smoothing over the entire bar list and returns
     * [smoothed+DM, smoothed-DM, smoothedTR] for the final period.
     */
    private static BigDecimal[] wilderSmoothedDMAndTR(List<OhlcBar> bars, int period) {

        // Initial sums for bars index 1..period
        BigDecimal sPDM = BigDecimal.ZERO;
        BigDecimal sMDM = BigDecimal.ZERO;
        BigDecimal sTR  = BigDecimal.ZERO;

        for (int i = 1; i <= period; i++) {
            OhlcBar curr = bars.get(i);
            OhlcBar prev = bars.get(i - 1);
            BigDecimal upMove   = curr.high().subtract(prev.high());
            BigDecimal downMove = prev.low().subtract(curr.low());
            sPDM = sPDM.add(plusDM(upMove, downMove));
            sMDM = sMDM.add(minusDM(upMove, downMove));
            sTR  = sTR.add(AtrCalculator.trueRange(curr, prev.close()));
        }

        // Wilder smoothing: smooth(n) = smooth(n-1) - smooth(n-1)/period + value(n)
        for (int i = period + 1; i < bars.size(); i++) {
            OhlcBar curr = bars.get(i);
            OhlcBar prev = bars.get(i - 1);
            BigDecimal upMove   = curr.high().subtract(prev.high());
            BigDecimal downMove = prev.low().subtract(curr.low());

            sPDM = sPDM
                    .subtract(sPDM.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP))
                    .add(plusDM(upMove, downMove));
            sMDM = sMDM
                    .subtract(sMDM.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP))
                    .add(minusDM(upMove, downMove));
            sTR  = sTR
                    .subtract(sTR.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP))
                    .add(AtrCalculator.trueRange(curr, prev.close()));
        }

        return new BigDecimal[]{sPDM, sMDM, sTR};
    }

    private static BigDecimal plusDM(BigDecimal upMove, BigDecimal downMove) {
        return upMove.compareTo(downMove) > 0 && upMove.compareTo(BigDecimal.ZERO) > 0
                ? upMove
                : BigDecimal.ZERO;
    }

    private static BigDecimal minusDM(BigDecimal upMove, BigDecimal downMove) {
        return downMove.compareTo(upMove) > 0 && downMove.compareTo(BigDecimal.ZERO) > 0
                ? downMove
                : BigDecimal.ZERO;
    }
}
