package pl.piomin.signalmind.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Persisted 20-day rolling average of 1-minute candle volume, grouped by
 * IST time slot (truncated to the minute).
 *
 * <p>One row per (stock, slot_time) pair. The scheduler rebuilds these rows
 * every post-market session via {@link pl.piomin.signalmind.stock.service.VolumeBaselineService}.
 *
 * <p>Schema (V3 migration):
 * <pre>
 *   CREATE TABLE volume_baselines (
 *       id          BIGSERIAL PRIMARY KEY,
 *       stock_id    BIGINT      NOT NULL REFERENCES stocks(id),
 *       slot_time   TIME        NOT NULL,
 *       avg_volume  BIGINT      NOT NULL,
 *       sample_days INT         NOT NULL DEFAULT 0,
 *       computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *       CONSTRAINT uq_volume_baselines_stock_slot UNIQUE (stock_id, slot_time)
 *   );
 * </pre>
 */
@Entity
@Table(name = "volume_baselines")
public class VolumeBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    /**
     * IST minute slot, e.g. {@code 09:15} or {@code 14:30}.
     * Stored as {@code TIME} in PostgreSQL (no date, no timezone).
     */
    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    /** Rounded mean volume across all sample days for this slot. */
    @Column(name = "avg_volume", nullable = false)
    private long avgVolume;

    /** Number of trading days that contributed to this average. */
    @Column(name = "sample_days", nullable = false)
    private int sampleDays;

    /** UTC instant at which this row was last computed. */
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected VolumeBaseline() {
    }

    public VolumeBaseline(Stock stock, LocalTime slotTime, long avgVolume,
                          int sampleDays, Instant computedAt) {
        this.stock = stock;
        this.slotTime = slotTime;
        this.avgVolume = avgVolume;
        this.sampleDays = sampleDays;
        this.computedAt = computedAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public Stock getStock() {
        return stock;
    }

    public LocalTime getSlotTime() {
        return slotTime;
    }

    public long getAvgVolume() {
        return avgVolume;
    }

    public int getSampleDays() {
        return sampleDays;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    @Override
    public String toString() {
        return "VolumeBaseline{stock=" + (stock != null ? stock.getSymbol() : "?")
                + ", slot=" + slotTime
                + ", avgVolume=" + avgVolume
                + ", sampleDays=" + sampleDays + "}";
    }
}
