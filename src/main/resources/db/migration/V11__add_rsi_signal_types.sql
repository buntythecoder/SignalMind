-- SM-24: Extend the signals type check constraint to include RSI reversal sub-types.
--
-- PostgreSQL does not support ALTER CONSTRAINT directly, so we drop the old
-- CHECK constraint and add a new one that includes RSI_OVERSOLD_BOUNCE and
-- RSI_OVERBOUGHT_REJECTION.

ALTER TABLE signals
    DROP CONSTRAINT IF EXISTS chk_signals_type;

ALTER TABLE signals
    ADD CONSTRAINT chk_signals_type CHECK (signal_type IN (
        'ORB',
        'VWAP_BREAKOUT',
        'VWAP_BREAKDOWN',
        'RSI_REVERSAL',
        'RSI_OVERSOLD_BOUNCE',
        'RSI_OVERBOUGHT_REJECTION',
        'GAP_FILL_LONG',
        'GAP_FILL_SHORT'
    ));
