-- V4: Generated trading signals

CREATE TABLE signals (
    id             BIGSERIAL PRIMARY KEY,
    stock_id       BIGINT        NOT NULL REFERENCES stocks(id),
    signal_type    VARCHAR(30)   NOT NULL,     -- ORB | VWAP_BREAKOUT | VWAP_BREAKDOWN | RSI_REVERSAL | GAP_FILL_LONG | GAP_FILL_SHORT
    direction      VARCHAR(5)    NOT NULL,     -- LONG | SHORT
    entry_price    NUMERIC(12,2) NOT NULL,
    target_price   NUMERIC(12,2) NOT NULL,
    stop_loss      NUMERIC(12,2) NOT NULL,
    confidence     INT           NOT NULL,     -- 0–100
    regime         VARCHAR(20)   NOT NULL,     -- TRENDING_UP | TRENDING_DOWN | SIDEWAYS | HIGH_VOLATILITY | CIRCUIT_HALT
    generated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    valid_until    TIMESTAMPTZ   NOT NULL,
    dispatched     BOOLEAN       NOT NULL DEFAULT FALSE,
    dispatched_at  TIMESTAMPTZ,
    CONSTRAINT chk_signals_type      CHECK (signal_type IN ('ORB','VWAP_BREAKOUT','VWAP_BREAKDOWN','RSI_REVERSAL','GAP_FILL_LONG','GAP_FILL_SHORT')),
    CONSTRAINT chk_signals_direction CHECK (direction IN ('LONG','SHORT')),
    CONSTRAINT chk_signals_confidence CHECK (confidence BETWEEN 0 AND 100),
    CONSTRAINT chk_signals_regime    CHECK (regime IN ('TRENDING_UP','TRENDING_DOWN','SIDEWAYS','HIGH_VOLATILITY','CIRCUIT_HALT'))
);

CREATE INDEX idx_signals_stock_time  ON signals (stock_id, generated_at DESC);
CREATE INDEX idx_signals_generated   ON signals (generated_at DESC);
CREATE INDEX idx_signals_dispatched  ON signals (dispatched) WHERE dispatched = FALSE;

COMMENT ON TABLE  signals            IS 'All generated signals. confidence >= 60 required before dispatch.';
COMMENT ON COLUMN signals.confidence IS '0–100 composite score. Gate: >= 60 to dispatch.';
COMMENT ON COLUMN signals.regime     IS 'Market regime at signal generation time';
