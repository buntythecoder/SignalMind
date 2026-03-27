-- V9: Mark candles that were synthesised due to missing ticks (SM-20).
-- is_synthetic = TRUE → O=H=L=C=prev Close, Volume=0; must not trigger signal detection.
ALTER TABLE candles
    ADD COLUMN IF NOT EXISTS is_synthetic BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN candles.is_synthetic IS 'TRUE when the candle was generated due to missing ticks in the 1-min window';
