package pl.piomin.signalmind.integration.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.IndexType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TelegramAlertFormatter} (SM-29).
 *
 * <p>All tests use a real {@code ObjectMapper} and no Spring context.
 */
class TelegramAlertFormatterTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private TelegramAlertFormatter formatter;
    private Stock stock;

    @BeforeEach
    void setUp() {
        formatter = new TelegramAlertFormatter(new ObjectMapper());
        stock     = new Stock("TCS", "Tata Consultancy Services", IndexType.NIFTY50);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Signal buildSignal(SignalType type, SignalDirection dir) {
        Instant generatedAt = LocalDateTime.of(2024, 1, 15, 9, 31)
                .atZone(IST).toInstant();
        Instant validUntil = LocalDateTime.of(2024, 1, 15, 10, 16)
                .atZone(IST).toInstant();

        BigDecimal target2 = (type == SignalType.ORB || type == SignalType.GAP_FILL_SHORT
                || type == SignalType.GAP_FILL_LONG)
                ? new BigDecimal("3540.00") : null;

        Signal signal = new Signal(
                stock,
                type,
                dir,
                new BigDecimal("3410.00"),
                new BigDecimal("3480.00"),
                target2,
                new BigDecimal("3380.00"),
                70,
                "TRENDING_UP",
                generatedAt,
                validUntil,
                type == SignalType.ORB ? new BigDecimal("3430.00") : null,
                type == SignalType.ORB ? new BigDecimal("3380.00") : null
        );
        signal.applyScores(50, 10, 10, 10, 8, 10);
        return signal;
    }

    // ── Format Tests ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Format content tests")
    class FormatTests {

        @Test
        @DisplayName("format_containsSymbol")
        void format_containsSymbol() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("TCS"), "Text should contain the stock symbol");
        }

        @Test
        @DisplayName("format_containsSignalTime")
        void format_containsSignalTime() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("09:31"), "Text should contain the signal generation time in IST");
        }

        @Test
        @DisplayName("format_containsConfidence")
        void format_containsConfidence() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("98%"),
                    "Text should contain the confidence percentage (50+10+10+10+8+10=98)");
        }

        @Test
        @DisplayName("format_containsEntryPrice")
        void format_containsEntryPrice() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("3410"), "Text should contain the entry price");
        }

        @Test
        @DisplayName("format_containsStopLoss")
        void format_containsStopLoss() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("3380"), "Text should contain the stop loss price");
        }

        @Test
        @DisplayName("format_containsTarget1")
        void format_containsTarget1() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("3480"), "Text should contain target 1 price");
        }

        @Test
        @DisplayName("format_containsTarget2_whenPresent")
        void format_containsTarget2_whenPresent() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("3540"), "Text should contain target 2 price when present");
            assertTrue(result.text().contains("Target 2"), "Text should contain the Target 2 label");
        }

        @Test
        @DisplayName("format_noTarget2_whenAbsent")
        void format_noTarget2_whenAbsent() {
            // VWAP_BREAKOUT with no target2
            Signal signal = new Signal(
                    stock,
                    SignalType.VWAP_BREAKOUT,
                    SignalDirection.LONG,
                    new BigDecimal("3410.00"),
                    new BigDecimal("3480.00"),
                    null,  // no target2
                    new BigDecimal("3380.00"),
                    70,
                    "TRENDING_UP",
                    LocalDateTime.of(2024, 1, 15, 9, 31).atZone(IST).toInstant(),
                    LocalDateTime.of(2024, 1, 15, 10, 16).atZone(IST).toInstant(),
                    null,
                    null
            );
            signal.applyScores(50, 10, 10, 10, 8, 10);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertFalse(result.text().contains("Target 2"), "Text should NOT contain Target 2 line when target2 is null");
        }

        @Test
        @DisplayName("format_containsSEBIDisclaimer")
        void format_containsSEBIDisclaimer() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("SEBI"), "Text should contain the SEBI disclaimer");
        }

        @Test
        @DisplayName("format_containsValidUntilTime")
        void format_containsValidUntilTime() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            // generatedAt = 09:31, validUntil = 10:16
            assertTrue(result.text().contains("10:16"), "Text should contain the valid-until time in IST");
        }

        @Test
        @DisplayName("format_containsSignalCount")
        void format_containsSignalCount() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 5L);
            assertTrue(result.text().contains("5"), "Text should contain the signalCountToday value");
        }

        @Test
        @DisplayName("format_gapFillShort_containsBrokerNote")
        void format_gapFillShort_containsBrokerNote() {
            Signal signal = buildSignal(SignalType.GAP_FILL_SHORT, SignalDirection.SHORT);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertTrue(result.text().contains("intraday short-selling"),
                    "GAP_FILL_SHORT alert should contain broker note about intraday short-selling");
        }

        @Test
        @DisplayName("format_gapFillLong_noBrokerNote")
        void format_gapFillLong_noBrokerNote() {
            Signal signal = buildSignal(SignalType.GAP_FILL_LONG, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertFalse(result.text().contains("Verify with your broker"),
                    "GAP_FILL_LONG alert should NOT contain broker note");
        }
    }

    // ── Prohibited Words Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Prohibited words must not appear in message text")
    class ProhibitedWordsTest {

        @ParameterizedTest(name = "prohibited word: \"{0}\"")
        @ValueSource(strings = {"buy", "sell", "invest", "guaranteed", "profit", "returns",
                                "recommendation", "advice", "tips"})
        void prohibitedWord_notInMessage(String word) {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertFalse(result.text().toLowerCase().contains(word),
                    "Message must not contain prohibited word: '" + word + "'");
        }
    }

    // ── Inline Keyboard Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Inline keyboard (reply markup) tests")
    class InlineKeyboardTest {

        @Test
        @DisplayName("replyMarkup_containsSignalId")
        void replyMarkup_containsSignalId() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            // Signal has no id (not persisted), so id is null → should use 0
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertNotNull(result.replyMarkupJson(), "replyMarkupJson should not be null");
            assertTrue(result.replyMarkupJson().contains("TOOK_TRADE:0"),
                    "replyMarkupJson should contain 'TOOK_TRADE:0' when signalId is null");
            assertTrue(result.replyMarkupJson().contains("WATCHING:0"),
                    "replyMarkupJson should contain 'WATCHING:0' when signalId is null");
        }

        @Test
        @DisplayName("replyMarkup_isValidJson")
        void replyMarkup_isValidJson() {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            assertNotNull(result.replyMarkupJson());
            assertDoesNotThrow(() -> new ObjectMapper().readTree(result.replyMarkupJson()),
                    "replyMarkupJson should be valid JSON");
        }

        @Test
        @DisplayName("replyMarkup_hasTwoButtons")
        void replyMarkup_hasTwoButtons() throws Exception {
            Signal signal = buildSignal(SignalType.ORB, SignalDirection.LONG);
            TelegramAlertFormatter.FormattedAlert result = formatter.format(signal, stock, 1L);
            JsonNode root = new ObjectMapper().readTree(result.replyMarkupJson());
            JsonNode row = root.path("inline_keyboard").get(0);
            assertNotNull(row, "inline_keyboard[0] should exist");
            assertEquals(2, row.size(), "inline_keyboard[0] should have exactly 2 buttons");
        }
    }

    // ── Type Label Tests ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Type label tests")
    class TypeLabelTest {

        @ParameterizedTest(name = "labelFor({0}) is non-blank")
        @EnumSource(SignalType.class)
        void labelFor_returnsNonBlankLabel(SignalType type) {
            String label = TelegramAlertFormatter.labelFor(type);
            assertNotNull(label, "Label for " + type + " should not be null");
            assertFalse(label.isBlank(), "Label for " + type + " should not be blank");
        }
    }
}
