package pl.piomin.signalmind.integration.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.piomin.signalmind.signal.detector.GapFillShortDetector;
import pl.piomin.signalmind.signal.domain.Signal;
import pl.piomin.signalmind.signal.domain.SignalType;
import pl.piomin.signalmind.stock.domain.Stock;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Formats a {@link Signal} into a rich Telegram HTML message with an inline keyboard (SM-29).
 *
 * <p>Produces a {@link FormattedAlert} containing:
 * <ul>
 *   <li>{@code text} — HTML-formatted message suitable for Telegram parse_mode=HTML</li>
 *   <li>{@code replyMarkupJson} — JSON string for a Telegram InlineKeyboardMarkup</li>
 * </ul>
 *
 * <p>The message text contains no prohibited words (buy, sell, invest, guaranteed, profit,
 * returns, recommendation, advice, tips) and ends with the standard SEBI disclaimer.
 */
@Component
public class TelegramAlertFormatter {

    private static final Logger log = LoggerFactory.getLogger(TelegramAlertFormatter.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    static final String SEBI_DISCLAIMER =
            "⚠️ <i>For educational and informational purposes only. " +
            "This platform is not registered with SEBI. " +
            "Independent due diligence required before any market action.</i>";

    private final ObjectMapper objectMapper;

    public TelegramAlertFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Formats the signal into a Telegram HTML message and an inline keyboard JSON string.
     *
     * @param signal            the signal to format
     * @param stock             the stock the signal belongs to
     * @param signalCountToday  total signals generated for this stock today (including this one)
     * @return a {@link FormattedAlert} with message text and reply markup JSON
     */
    public FormattedAlert format(Signal signal, Stock stock, long signalCountToday) {
        String typeLabel = labelFor(signal.getSignalType());
        String direction = signal.getDirection().name();

        String signalTime  = signal.getGeneratedAt().atZone(IST).toLocalTime().format(HH_MM);
        String validUntil  = signal.getValidUntil().atZone(IST).toLocalTime().format(HH_MM);

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("📊 <b>").append(typeLabel).append(" ").append(direction)
          .append("</b> | ").append(stock.getSymbol()).append("\n");
        sb.append("⏰ ").append(signalTime).append(" IST | Confidence: ")
          .append(signal.getConfidence()).append("%\n");
        sb.append("\n");

        // Price levels
        sb.append("📍 Entry Zone: ₹").append(signal.getEntryPrice()).append("\n");
        sb.append("🛡 Stop Reference: ₹").append(signal.getStopLoss()).append("\n");
        sb.append("🎯 Target 1: ₹").append(signal.getTargetPrice()).append("\n");
        if (signal.getTarget2() != null) {
            sb.append("🎯 Target 2: ₹").append(signal.getTarget2()).append("\n");
        }
        sb.append("\n");

        // Analysis Notes
        sb.append("<b>Analysis Notes:</b>\n");

        String volumeBullet = volumeBullet(signal.getScoreVolume());
        if (!volumeBullet.isEmpty()) {
            sb.append(volumeBullet);
        }

        String regimeDisplay = signal.getRegime().replace("_", " ");
        sb.append("• Market Regime: ").append(regimeDisplay).append("\n");
        sb.append("• Pattern: ").append(typeLabel).append("\n");
        sb.append("\n");

        // Validity
        sb.append("⏳ Valid until: ").append(validUntil).append(" IST\n");
        sb.append("📈 Signals for this stock today: ").append(signalCountToday).append("\n");
        sb.append("\n");

        // Broker note only for GAP_FILL_SHORT
        if (signal.getSignalType() == SignalType.GAP_FILL_SHORT) {
            sb.append("⚠️ ").append(GapFillShortDetector.BROKER_NOTE).append("\n\n");
        }

        // SEBI disclaimer
        sb.append(SEBI_DISCLAIMER);

        String text = sb.toString();
        String replyMarkupJson = buildReplyMarkup(signal.getId());

        return new FormattedAlert(text, replyMarkupJson);
    }

    /**
     * Immutable result of formatting a signal.
     *
     * @param text             HTML-formatted message body
     * @param replyMarkupJson  JSON string for Telegram InlineKeyboardMarkup, or null on error
     */
    public record FormattedAlert(String text, String replyMarkupJson) {}

    // ── Package-private helpers ───────────────────────────────────────────────

    /**
     * Returns the human-readable label for a signal type.
     */
    static String labelFor(SignalType type) {
        return switch (type) {
            case ORB                       -> "ORB";
            case VWAP_BREAKOUT             -> "VWAP Breakout";
            case VWAP_BREAKDOWN            -> "VWAP Breakdown";
            case RSI_OVERSOLD_BOUNCE       -> "RSI Oversold Bounce";
            case RSI_OVERBOUGHT_REJECTION  -> "RSI Overbought Rejection";
            case GAP_FILL_LONG             -> "Gap Fill";
            case GAP_FILL_SHORT            -> "Gap Fill";
            case RSI_REVERSAL              -> "RSI Reversal";
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String volumeBullet(Integer scoreVolume) {
        if (scoreVolume != null && scoreVolume >= 15) {
            return "• Exceptional volume surge (3×+ above baseline)\n";
        }
        if (scoreVolume != null && scoreVolume >= 10) {
            return "• Strong volume confirmation (2×+ above baseline)\n";
        }
        if (scoreVolume != null && scoreVolume >= 5) {
            return "• Above-average volume observed\n";
        }
        return "";
    }

    private String buildReplyMarkup(Long signalId) {
        long id = signalId != null ? signalId : 0L;
        Map<String, Object> tookTrade = Map.of(
                "text", "✅ Took Trade",
                "callback_data", "TOOK_TRADE:" + id
        );
        Map<String, Object> watching = Map.of(
                "text", "👀 Watching",
                "callback_data", "WATCHING:" + id
        );
        Map<String, Object> markup = Map.of(
                "inline_keyboard", List.of(List.of(tookTrade, watching))
        );
        try {
            return objectMapper.writeValueAsString(markup);
        } catch (JsonProcessingException e) {
            log.warn("[telegram-formatter] Failed to serialize reply markup for signalId={}: {}", id, e.getMessage());
            return null;
        }
    }
}
