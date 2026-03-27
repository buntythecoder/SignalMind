package pl.piomin.signalmind.quality.service;

import org.springframework.stereotype.Component;
import pl.piomin.signalmind.quality.domain.CandleIssue;
import pl.piomin.signalmind.quality.domain.QualityIssue;
import pl.piomin.signalmind.stock.domain.Candle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates Open-High-Low-Close-Volume consistency for each candle.
 *
 * <p>Rules applied per candle (in order):
 * <ol>
 *   <li>If any of O/H/L/C is {@code null} or {@code <= 0}: emit
 *       {@link QualityIssue#ZERO_OR_NEGATIVE_PRICE} and skip the remaining price checks.</li>
 *   <li>H &lt; L → {@link QualityIssue#HIGH_BELOW_LOW}</li>
 *   <li>H &lt; O → {@link QualityIssue#HIGH_BELOW_OPEN}</li>
 *   <li>H &lt; C → {@link QualityIssue#HIGH_BELOW_CLOSE}</li>
 *   <li>L &gt; O → {@link QualityIssue#LOW_ABOVE_OPEN}</li>
 *   <li>L &gt; C → {@link QualityIssue#LOW_ABOVE_CLOSE}</li>
 *   <li>volume &lt; 0 → {@link QualityIssue#NEGATIVE_VOLUME}</li>
 * </ol>
 */
@Component
public class OhlcvIntegrityValidator implements DataQualityValidator {

    @Override
    public String validatorName() {
        return "ohlcv-integrity";
    }

    @Override
    public List<CandleIssue> validate(String symbol, LocalDate tradingDay, List<Candle> candles) {
        List<CandleIssue> issues = new ArrayList<>();

        for (Candle candle : candles) {
            BigDecimal open = candle.getOpen();
            BigDecimal high = candle.getHigh();
            BigDecimal low = candle.getLow();
            BigDecimal close = candle.getClose();

            if (hasNullOrNonPositivePrice(open, high, low, close)) {
                issues.add(new CandleIssue(
                        candle.getCandleTime(),
                        QualityIssue.ZERO_OR_NEGATIVE_PRICE,
                        "One or more of O/H/L/C is null, zero, or negative: "
                                + "O=" + open + " H=" + high + " L=" + low + " C=" + close
                ));
                // volume check still applies even when prices are bad
                if (candle.getVolume() < 0) {
                    issues.add(new CandleIssue(
                            candle.getCandleTime(),
                            QualityIssue.NEGATIVE_VOLUME,
                            "volume=" + candle.getVolume()
                    ));
                }
                continue;
            }

            // All four prices are non-null and positive; run relational checks.
            if (high.compareTo(low) < 0) {
                issues.add(new CandleIssue(
                        candle.getCandleTime(),
                        QualityIssue.HIGH_BELOW_LOW,
                        "H=" + high + " < L=" + low
                ));
            }
            if (high.compareTo(open) < 0) {
                issues.add(new CandleIssue(
                        candle.getCandleTime(),
                        QualityIssue.HIGH_BELOW_OPEN,
                        "H=" + high + " < O=" + open
                ));
            }
            if (high.compareTo(close) < 0) {
                issues.add(new CandleIssue(
                        candle.getCandleTime(),
                        QualityIssue.HIGH_BELOW_CLOSE,
                        "H=" + high + " < C=" + close
                ));
            }
            if (low.compareTo(open) > 0) {
                issues.add(new CandleIssue(
                        candle.getCandleTime(),
                        QualityIssue.LOW_ABOVE_OPEN,
                        "L=" + low + " > O=" + open
                ));
            }
            if (low.compareTo(close) > 0) {
                issues.add(new CandleIssue(
                        candle.getCandleTime(),
                        QualityIssue.LOW_ABOVE_CLOSE,
                        "L=" + low + " > C=" + close
                ));
            }
            if (candle.getVolume() < 0) {
                issues.add(new CandleIssue(
                        candle.getCandleTime(),
                        QualityIssue.NEGATIVE_VOLUME,
                        "volume=" + candle.getVolume()
                ));
            }
        }

        return issues;
    }

    private boolean hasNullOrNonPositivePrice(BigDecimal open, BigDecimal high,
                                               BigDecimal low, BigDecimal close) {
        return isNullOrNonPositive(open)
                || isNullOrNonPositive(high)
                || isNullOrNonPositive(low)
                || isNullOrNonPositive(close);
    }

    private boolean isNullOrNonPositive(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0;
    }
}
