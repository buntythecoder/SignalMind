package pl.piomin.signalmind.integration.breeze.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * One OHLCV bar from the Breeze {@code /historicalcharts} response array.
 *
 * <p>Field names match the Breeze API JSON keys exactly.
 */
public record BreezeOhlcv(
        @JsonProperty("datetime") String datetime,
        @JsonProperty("open")     BigDecimal open,
        @JsonProperty("high")     BigDecimal high,
        @JsonProperty("low")      BigDecimal low,
        @JsonProperty("close")    BigDecimal close,
        @JsonProperty("volume")   long volume
) {
}
