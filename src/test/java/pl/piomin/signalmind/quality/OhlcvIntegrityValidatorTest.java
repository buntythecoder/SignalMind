package pl.piomin.signalmind.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.quality.domain.CandleIssue;
import pl.piomin.signalmind.quality.domain.QualityIssue;
import pl.piomin.signalmind.quality.service.OhlcvIntegrityValidator;
import pl.piomin.signalmind.stock.domain.Candle;
import pl.piomin.signalmind.stock.domain.CandleSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OhlcvIntegrityValidator}.
 * No Spring context — pure logic verification.
 */
class OhlcvIntegrityValidatorTest {

    private OhlcvIntegrityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OhlcvIntegrityValidator();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a candle with the given field values.
     * Stock is null because the OHLCV validator does not inspect the Stock reference.
     */
    private Candle candle(double open, double high, double low, double close, long volume) {
        return new Candle(
                null,                          // stock — not used by this validator
                Instant.parse("2024-01-15T03:45:00Z"),
                bd(open), bd(high), bd(low), bd(close),
                volume,
                CandleSource.HIST
        );
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void validCandle_noIssues() {
        Candle candle = candle(100, 102, 98, 101, 1000);
        List<CandleIssue> issues = validator.validate("RELIANCE", LocalDate.of(2024, 1, 15), List.of(candle));
        assertThat(issues).isEmpty();
    }

    @Test
    void highBelowLow_detected() {
        // H=97, L=98 — impossible: high is below low
        Candle candle = candle(100, 97, 98, 100, 1000);
        List<CandleIssue> issues = validator.validate("RELIANCE", LocalDate.of(2024, 1, 15), List.of(candle));

        assertThat(issues)
                .extracting(CandleIssue::issueType)
                .contains(QualityIssue.HIGH_BELOW_LOW);
    }

    @Test
    void zeroPriceRejected() {
        // open = 0 → ZERO_OR_NEGATIVE_PRICE; no other price-based issue should be added
        Candle candle = candle(0, 102, 98, 101, 1000);
        List<CandleIssue> issues = validator.validate("RELIANCE", LocalDate.of(2024, 1, 15), List.of(candle));

        assertThat(issues)
                .hasSize(1)
                .extracting(CandleIssue::issueType)
                .containsExactly(QualityIssue.ZERO_OR_NEGATIVE_PRICE);
    }

    @Test
    void negativeVolume_detected() {
        // Prices are valid; volume is negative
        Candle candle = candle(100, 102, 98, 101, -1);
        List<CandleIssue> issues = validator.validate("RELIANCE", LocalDate.of(2024, 1, 15), List.of(candle));

        assertThat(issues)
                .extracting(CandleIssue::issueType)
                .containsExactly(QualityIssue.NEGATIVE_VOLUME);
    }
}
