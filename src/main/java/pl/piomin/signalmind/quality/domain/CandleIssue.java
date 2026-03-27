package pl.piomin.signalmind.quality.domain;

import java.time.Instant;

/**
 * A single data-quality problem associated with one candle timestamp.
 *
 * @param candleTime the UTC timestamp of the affected (or expected) candle
 * @param issueType  the category of quality problem
 * @param detail     a human-readable description with specifics (e.g. field values)
 */
public record CandleIssue(
        Instant candleTime,
        QualityIssue issueType,
        String detail
) {
}
