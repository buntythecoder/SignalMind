package pl.piomin.signalmind.integration.breeze.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level response from the Breeze {@code GET /historicalcharts} endpoint.
 *
 * <pre>
 * {
 *   "Success": "true",
 *   "Status":  200,
 *   "Error":   null,
 *   "Success": [ { "datetime": "...", "open": ..., ... }, ... ]
 * }
 * </pre>
 *
 * Note: the Breeze API re-uses the key {@code "Success"} for both the status flag and
 * the data array. Jackson maps the last occurrence; use {@code status} and {@code data}
 * as separate aliases to avoid the collision.
 */
public record BreezeHistoricalResponse(
        @JsonProperty("Status")  int status,
        @JsonProperty("Error")   String error,
        @JsonProperty("Success") List<BreezeOhlcv> data
) {
    public boolean isSuccess() {
        return status == 200 && (error == null || error.isBlank());
    }
}
