package pl.piomin.signalmind.regime.indicator;

import java.math.BigDecimal;

/**
 * Lightweight OHLC data transfer record used by indicator calculators.
 *
 * <p>Decouples the indicator utilities from the JPA {@code Candle} entity so
 * they remain pure, stateless, and easily unit-testable (SM-21).
 */
public record OhlcBar(
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close
) {}
