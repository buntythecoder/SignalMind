package pl.piomin.signalmind.candle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.candle.domain.VwapState;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VwapState}.
 *
 * <p>No Spring context required — pure domain logic.
 *
 * <p>SM-19
 */
class VwapStateTest {

    private VwapState state;

    @BeforeEach
    void setUp() {
        state = new VwapState();
    }

    // ── Zero-data guard ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Before any updates, VWAP, upper band, and lower band are all zero")
    void zeroVolume_vwapIsZero() {
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                state.currentVwap().setScale(4, RoundingMode.HALF_UP),
                "VWAP should be 0 before any data");
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                state.upperBand().setScale(4, RoundingMode.HALF_UP),
                "upperBand should be 0 before any data");
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                state.lowerBand().setScale(4, RoundingMode.HALF_UP),
                "lowerBand should be 0 before any data");
    }

    @Test
    @DisplayName("Zero-volume update is silently ignored; VWAP stays zero")
    void zeroVolumeUpdate_isIgnored() {
        state.update(bd("120.00"), bd("118.00"), bd("119.00"), 0L);
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                state.currentVwap().setScale(4, RoundingMode.HALF_UP),
                "Zero-volume update must not change VWAP");
    }

    // ── Single-update accuracy ────────────────────────────────────────────────

    @Test
    @DisplayName("After single update, VWAP equals typical price (H+L+C)/3")
    void singleUpdate_vwapEqualsTP() {
        // TP = (120 + 118 + 119) / 3 = 357 / 3 = 119.0000
        state.update(bd("120.00"), bd("118.00"), bd("119.00"), 1000L);

        BigDecimal expectedVwap = new BigDecimal("119.0000");
        assertEquals(expectedVwap, state.currentVwap(),
                "Single-candle VWAP must equal TP = (H+L+C)/3");
    }

    @Test
    @DisplayName("After single update, std dev is zero so bands equal VWAP")
    void singleUpdate_bandsEqualVwap() {
        state.update(bd("120.00"), bd("118.00"), bd("119.00"), 1000L);

        BigDecimal vwap = state.currentVwap();
        // With one data point, variance and std dev are 0 → bands = VWAP
        assertEquals(vwap, state.upperBand(), "upperBand must equal VWAP when std dev is 0");
        assertEquals(vwap, state.lowerBand(), "lowerBand must equal VWAP when std dev is 0");
    }

    // ── Two-update weighted average ───────────────────────────────────────────

    @Test
    @DisplayName("Two updates: VWAP is volume-weighted average of typical prices")
    void twoUpdates_vwapWeightedByVolume() {
        // Candle 1: TP = (110 + 108 + 109) / 3 = 109.0,  volume = 1000
        // Candle 2: TP = (130 + 126 + 128) / 3 = 128.0,  volume = 3000
        // Expected VWAP = (109×1000 + 128×3000) / (1000+3000)
        //               = (109000 + 384000) / 4000
        //               = 493000 / 4000
        //               = 123.25
        state.update(bd("110.00"), bd("108.00"), bd("109.00"), 1000L);
        state.update(bd("130.00"), bd("126.00"), bd("128.00"), 3000L);

        BigDecimal expected = new BigDecimal("123.2500");
        assertEquals(expected, state.currentVwap(),
                "VWAP must be volume-weighted: (109×1000 + 128×3000) / 4000 = 123.2500");
    }

    // ── Band ordering ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("After two different-price updates, upper band is strictly above VWAP")
    void upperBandAboveVwap() {
        state.update(bd("110.00"), bd("108.00"), bd("109.00"), 1000L);
        state.update(bd("130.00"), bd("126.00"), bd("128.00"), 3000L);

        BigDecimal vwap  = state.currentVwap();
        BigDecimal upper = state.upperBand();

        assertTrue(upper.compareTo(vwap) > 0,
                "upperBand (" + upper + ") must be strictly above VWAP (" + vwap + ")");
    }

    @Test
    @DisplayName("After two different-price updates, lower band is strictly below VWAP")
    void lowerBandBelowVwap() {
        state.update(bd("110.00"), bd("108.00"), bd("109.00"), 1000L);
        state.update(bd("130.00"), bd("126.00"), bd("128.00"), 3000L);

        BigDecimal vwap  = state.currentVwap();
        BigDecimal lower = state.lowerBand();

        assertTrue(lower.compareTo(vwap) < 0,
                "lowerBand (" + lower + ") must be strictly below VWAP (" + vwap + ")");
    }

    @Test
    @DisplayName("Bands are symmetric: upper − VWAP equals VWAP − lower")
    void bandsAreSymmetric() {
        state.update(bd("110.00"), bd("108.00"), bd("109.00"), 1000L);
        state.update(bd("130.00"), bd("126.00"), bd("128.00"), 3000L);

        BigDecimal vwap  = state.currentVwap();
        BigDecimal upper = state.upperBand();
        BigDecimal lower = state.lowerBand();

        BigDecimal upperDiff = upper.subtract(vwap).abs();
        BigDecimal lowerDiff = vwap.subtract(lower).abs();

        // Allow tiny rounding difference at scale 4
        assertTrue(upperDiff.subtract(lowerDiff).abs().compareTo(new BigDecimal("0.0002")) <= 0,
                "Bands must be symmetric around VWAP: upper-vwap=" + upperDiff
                        + " lower-vwap=" + lowerDiff);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
