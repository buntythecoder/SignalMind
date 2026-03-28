package pl.piomin.signalmind.signal.dto;

import java.time.Instant;

/**
 * Aggregated stats for the real-time dashboard header (SM-35).
 *
 * <p>{@code currentRegime}, {@code regimeReason}, and {@code regimeCalculatedAt}
 * are {@code null} when Redis is unavailable or no regime has been computed yet.
 */
public record DashboardStatsDto(
        int totalToday,
        int wins,
        int losses,
        int active,
        double winRate,
        String currentRegime,
        String regimeReason,
        Instant regimeCalculatedAt
) {
}
