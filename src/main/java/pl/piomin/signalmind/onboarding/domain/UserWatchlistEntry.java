package pl.piomin.signalmind.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Represents a single entry in a user's stock watchlist (SM-34).
 *
 * <p>An empty watchlist means the user receives alerts for all active stocks.
 * Populating the watchlist restricts alerts to the listed stocks only.
 */
@Entity
@Table(name = "user_watchlist")
public class UserWatchlistEntry {

    @EmbeddedId
    private UserWatchlistId id;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected UserWatchlistEntry() {
    }

    public UserWatchlistEntry(UserWatchlistId id, Instant addedAt) {
        this.id = id;
        this.addedAt = addedAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UserWatchlistId getId() {
        return id;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
