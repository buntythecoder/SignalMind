-- SM-32: Post-market outcome tracker — records the final outcome of each signal.
-- One row per signal (enforced by uq_signal_outcome).

CREATE TABLE signal_outcomes (
    id              BIGSERIAL PRIMARY KEY,
    signal_id       BIGINT NOT NULL REFERENCES signals(id) ON DELETE CASCADE,
    outcome         VARCHAR(20) NOT NULL
        CONSTRAINT chk_outcome CHECK (outcome IN ('TARGET_1_HIT','TARGET_2_HIT','STOP_HIT','EXPIRED')),
    exit_price      NUMERIC(12,2),
    exit_time       TIMESTAMPTZ,
    pnl_points      NUMERIC(10,2),
    mae             NUMERIC(10,2),   -- Maximum Adverse Excursion
    mfe             NUMERIC(10,2),   -- Maximum Favorable Excursion
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_signal_outcome UNIQUE (signal_id)
);

CREATE INDEX idx_signal_outcomes_recorded_at ON signal_outcomes (recorded_at);
