package pl.piomin.signalmind.onboarding.dto;

/**
 * Stock summary DTO used in the watchlist response (SM-34).
 * The {@code inWatchlist} flag indicates whether the current user has explicitly added this stock.
 */
public record StockDto(
        Long id,
        String symbol,
        String companyName,
        String sector,
        String indexType,
        boolean inWatchlist
) {
}
