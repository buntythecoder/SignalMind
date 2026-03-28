package pl.piomin.signalmind.signal.dto;

import pl.piomin.signalmind.signal.domain.SignalDirection;
import pl.piomin.signalmind.signal.domain.SignalStatus;
import pl.piomin.signalmind.signal.domain.SignalType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only projection of a {@link pl.piomin.signalmind.signal.domain.Signal}
 * sent to the React dashboard (SM-35).
 */
public record SignalDto(
        Long id,
        String stockSymbol,
        String companyName,
        String sector,
        String indexType,
        SignalType signalType,
        SignalDirection direction,
        BigDecimal entryPrice,
        BigDecimal targetPrice,
        BigDecimal target2,
        BigDecimal stopLoss,
        int confidence,
        String regime,
        SignalStatus status,
        Instant generatedAt,
        Instant validUntil
) {
}
