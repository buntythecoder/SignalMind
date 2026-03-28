package pl.piomin.signalmind.onboarding.domain;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link UserWatchlistEntry}.
 * Maps to the (user_id, stock_id) primary key of the user_watchlist table (SM-34).
 */
@Embeddable
public class UserWatchlistId implements Serializable {

    private Long userId;
    private Long stockId;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected UserWatchlistId() {
    }

    public UserWatchlistId(Long userId, Long stockId) {
        this.userId = userId;
        this.stockId = stockId;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getUserId() {
        return userId;
    }

    public Long getStockId() {
        return stockId;
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserWatchlistId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(stockId, that.stockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, stockId);
    }
}
