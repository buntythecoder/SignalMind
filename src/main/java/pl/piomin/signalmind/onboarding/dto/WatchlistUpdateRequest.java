package pl.piomin.signalmind.onboarding.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for PUT /api/onboarding/watchlist (SM-34).
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code null} stockIds — subscribe to all active stocks (clears the watchlist table rows)</li>
 *   <li>empty list    — disable all alerts (no rows = no alerts in that interpretation)</li>
 *   <li>non-empty list — receive alerts only for the listed stock IDs</li>
 * </ul>
 */
public record WatchlistUpdateRequest(
        @NotNull List<Long> stockIds   // empty list = no alerts; null = subscribe all
) {
}
