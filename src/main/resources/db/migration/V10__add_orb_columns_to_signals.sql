-- V10: Add ORB-specific columns to signals (T2 and opening range boundaries)
ALTER TABLE signals
    ADD COLUMN IF NOT EXISTS target2  NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS orb_high NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS orb_low  NUMERIC(12,2);
