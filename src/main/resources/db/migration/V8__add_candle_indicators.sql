-- V8: Add VWAP and RSI indicator columns to the candles table.
-- NULL for HIST candles; populated by MinuteCandleAssembler for LIVE candles (SM-19).
ALTER TABLE candles
    ADD COLUMN IF NOT EXISTS vwap         NUMERIC(12,4),
    ADD COLUMN IF NOT EXISTS vwap_upper   NUMERIC(12,4),
    ADD COLUMN IF NOT EXISTS vwap_lower   NUMERIC(12,4),
    ADD COLUMN IF NOT EXISTS rsi          NUMERIC(6,2);

COMMENT ON COLUMN candles.vwap        IS 'Cumulative session VWAP (TP×V / ΣV) from 09:15 IST, resets daily';
COMMENT ON COLUMN candles.vwap_upper  IS 'VWAP + 1 × VWAP standard deviation band';
COMMENT ON COLUMN candles.vwap_lower  IS 'VWAP − 1 × VWAP standard deviation band';
COMMENT ON COLUMN candles.rsi         IS 'RSI(14) Wilder smoothed on 1-min close prices, rolling across days';
