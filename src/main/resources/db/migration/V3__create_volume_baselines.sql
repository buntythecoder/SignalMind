-- V3: Per-minute volume baselines (60 stocks × 195 slots = 11,700 rows)
-- Rebuilt nightly by the volume baseline job.

CREATE TABLE volume_baselines (
    id            BIGSERIAL PRIMARY KEY,
    stock_id      BIGINT       NOT NULL REFERENCES stocks(id),
    slot_time     TIME         NOT NULL,   -- e.g. '09:15:00'
    avg_volume    BIGINT       NOT NULL,
    sample_days   INT          NOT NULL DEFAULT 0,
    computed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_volume_baselines_stock_slot UNIQUE (stock_id, slot_time)
);

CREATE INDEX idx_volume_baselines_stock ON volume_baselines (stock_id);

COMMENT ON TABLE  volume_baselines            IS '60 × 195 = 11,700 rows. Rebuilt nightly from 20-day rolling window.';
COMMENT ON COLUMN volume_baselines.slot_time  IS 'Minute-start time e.g. 09:15:00 through 15:29:00';
COMMENT ON COLUMN volume_baselines.avg_volume IS '20-day average volume for this stock at this minute slot';
