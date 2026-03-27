package pl.piomin.signalmind.signal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted outcome of a {@link Signal} at the end of the trading session (SM-32).
 *
 * <p>Maps to the {@code signal_outcomes} table created in V16.  At most one row exists
 * per signal — enforced by the {@code uq_signal_outcome} unique constraint on {@code signal_id}.
 *
 * <p>Outcome values must match the {@code chk_outcome} CHECK constraint:
 * {@code TARGET_1_HIT, TARGET_2_HIT, STOP_HIT, EXPIRED}.
 * MARKET_CLOSE signals are recorded with {@code EXPIRED} (closed without a target or stop).
 */
@Entity
@Table(name = "signal_outcomes")
public class SignalOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "exit_price", precision = 12, scale = 2)
    private BigDecimal exitPrice;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "pnl_points", precision = 10, scale = 2)
    private BigDecimal pnlPoints;

    @Column(name = "mae", precision = 10, scale = 2)
    private BigDecimal mae;

    @Column(name = "mfe", precision = 10, scale = 2)
    private BigDecimal mfe;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Required by JPA. Not for application use. */
    protected SignalOutcome() {
    }

    /**
     * Creates a new outcome record.
     *
     * @param signalId   the ID of the signal this outcome belongs to
     * @param outcome    outcome code (TARGET_1_HIT, TARGET_2_HIT, STOP_HIT, EXPIRED)
     * @param exitPrice  the price at which the signal was resolved
     * @param exitTime   the instant the signal reached its terminal state
     * @param pnlPoints  profit/loss in price points (positive = profit, negative = loss)
     */
    public SignalOutcome(Long signalId, String outcome, BigDecimal exitPrice,
                         Instant exitTime, BigDecimal pnlPoints) {
        this.signalId   = signalId;
        this.outcome    = outcome;
        this.exitPrice  = exitPrice;
        this.exitTime   = exitTime;
        this.pnlPoints  = pnlPoints;
        this.recordedAt = Instant.now();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public Long getSignalId() {
        return signalId;
    }

    public String getOutcome() {
        return outcome;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public BigDecimal getPnlPoints() {
        return pnlPoints;
    }

    public BigDecimal getMae() {
        return mae;
    }

    public BigDecimal getMfe() {
        return mfe;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    // ── Setters (MAE/MFE set post-construction) ───────────────────────────────

    public void setMae(BigDecimal mae) {
        this.mae = mae;
    }

    public void setMfe(BigDecimal mfe) {
        this.mfe = mfe;
    }

    @Override
    public String toString() {
        return "SignalOutcome{signalId=" + signalId
                + ", outcome=" + outcome
                + ", pnl=" + pnlPoints + "}";
    }
}
