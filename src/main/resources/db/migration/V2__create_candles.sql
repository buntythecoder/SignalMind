-- V2: Intraday OHLCV candles (1-minute resolution)
-- Partitioned by month for efficient range queries and purging.

CREATE TABLE candles (
    id          BIGSERIAL,
    stock_id    BIGINT       NOT NULL REFERENCES stocks(id),
    candle_time TIMESTAMPTZ  NOT NULL,   -- minute-start UTC
    open        NUMERIC(12,2) NOT NULL,
    high        NUMERIC(12,2) NOT NULL,
    low         NUMERIC(12,2) NOT NULL,
    close       NUMERIC(12,2) NOT NULL,
    volume      BIGINT        NOT NULL DEFAULT 0,
    source      VARCHAR(10)   NOT NULL DEFAULT 'LIVE',   -- LIVE | HIST
    PRIMARY KEY (id, candle_time),
    CONSTRAINT uq_candles_stock_time UNIQUE (stock_id, candle_time),
    CONSTRAINT chk_candles_source CHECK (source IN ('LIVE','HIST'))
) PARTITION BY RANGE (candle_time);

-- Create initial monthly partitions (current month + next)
CREATE TABLE candles_2024_01 PARTITION OF candles
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE candles_2024_02 PARTITION OF candles
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Add further partitions via application or pg_partman as needed.

CREATE INDEX idx_candles_stock_time ON candles (stock_id, candle_time DESC);
CREATE INDEX idx_candles_time       ON candles (candle_time DESC);

COMMENT ON TABLE  candles             IS '1-minute OHLCV candles. Partitioned by month.';
COMMENT ON COLUMN candles.source      IS 'LIVE = from Angel One WebSocket; HIST = from ICICI Breeze history API';
