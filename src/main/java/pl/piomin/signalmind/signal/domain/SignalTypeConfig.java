package pl.piomin.signalmind.signal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Runtime feature-flag entity for each signal type (SM-27).
 *
 * <p>Rows in {@code signal_type_config} are read at engine startup and refreshed
 * every 5 minutes.  Setting {@code enabled = false} for a signal type causes the
 * engine to skip that detector within 5 minutes — no restart required.
 */
@Entity
@Table(name = "signal_type_config")
public class SignalTypeConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", length = 30, nullable = false)
    private SignalType signalType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. Not for application use. */
    protected SignalTypeConfig() {
    }

    public SignalTypeConfig(SignalType signalType, boolean enabled) {
        this.signalType = signalType;
        this.enabled    = enabled;
        this.updatedAt  = Instant.now();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public SignalType getSignalType() {
        return signalType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        this.enabled   = enabled;
        this.updatedAt = Instant.now();
    }
}
