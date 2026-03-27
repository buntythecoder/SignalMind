-- SM-27: signal_type_config — runtime feature flags per signal type.
-- Rows can be toggled via SQL/admin API; the engine refreshes every 5 minutes.

CREATE TABLE signal_type_config (
    signal_type VARCHAR(30) PRIMARY KEY,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_stc_signal_type CHECK (signal_type IN (
        'ORB', 'VWAP_BREAKOUT', 'VWAP_BREAKDOWN',
        'RSI_OVERSOLD_BOUNCE', 'RSI_OVERBOUGHT_REJECTION',
        'GAP_FILL_LONG', 'GAP_FILL_SHORT'
    ))
);

-- Seed: all signal types enabled by default
INSERT INTO signal_type_config (signal_type) VALUES
    ('ORB'),
    ('VWAP_BREAKOUT'),
    ('VWAP_BREAKDOWN'),
    ('RSI_OVERSOLD_BOUNCE'),
    ('RSI_OVERBOUGHT_REJECTION'),
    ('GAP_FILL_LONG'),
    ('GAP_FILL_SHORT');
