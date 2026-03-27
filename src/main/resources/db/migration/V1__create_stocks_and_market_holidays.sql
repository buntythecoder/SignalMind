-- V1: Core reference tables — stocks and market holidays
-- Flyway migration: V1__create_stocks_and_market_holidays.sql

CREATE TABLE stocks (
    id           BIGSERIAL PRIMARY KEY,
    symbol       VARCHAR(20)  NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    index_type   VARCHAR(20)  NOT NULL,        -- NIFTY50 | BANKNIFTY | NIFTY_INDEX | INDIA_VIX
    breeze_code  VARCHAR(20),
    angel_token  VARCHAR(20),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stocks_symbol UNIQUE (symbol),
    CONSTRAINT chk_stocks_index_type CHECK (index_type IN ('NIFTY50','BANKNIFTY','NIFTY_INDEX','INDIA_VIX'))
);

CREATE INDEX idx_stocks_index_type ON stocks (index_type) WHERE active = TRUE;
CREATE INDEX idx_stocks_active      ON stocks (active);

COMMENT ON TABLE  stocks              IS 'NSE/BSE trading instruments in the SignalMind universe (60 stocks + 2 indices)';
COMMENT ON COLUMN stocks.index_type   IS 'Enum: NIFTY50 | BANKNIFTY | NIFTY_INDEX | INDIA_VIX';
COMMENT ON COLUMN stocks.breeze_code  IS 'ICICI Breeze instrument code for historical OHLCV pulls';
COMMENT ON COLUMN stocks.angel_token  IS 'Angel One instrument token for live WebSocket ticks';

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE market_holidays (
    id           BIGSERIAL PRIMARY KEY,
    holiday_date DATE         NOT NULL,
    description  VARCHAR(100),
    CONSTRAINT uq_market_holidays_date UNIQUE (holiday_date)
);

CREATE INDEX idx_market_holidays_date ON market_holidays (holiday_date);

COMMENT ON TABLE market_holidays IS 'NSE/BSE market holiday calendar. Updated annually.';
