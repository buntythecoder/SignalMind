-- SM-31: Add status column to signals table for intraday signal lifecycle tracking.
-- Default is GENERATED (the state every new signal starts in).
ALTER TABLE signals
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'GENERATED'
        CONSTRAINT chk_signal_status CHECK (status IN (
            'GENERATED','TRIGGERED','TARGET_1_HIT','TARGET_2_HIT',
            'STOP_HIT','EXPIRED','MARKET_CLOSE'
        ));

-- Partial index on the two "active" statuses that the status updater polls every minute.
-- Keeping the index partial avoids bloat from the majority of signals that are terminal.
CREATE INDEX idx_signals_status ON signals (status)
    WHERE status IN ('GENERATED','TRIGGERED');
