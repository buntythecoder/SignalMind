-- V6: Add sector column to stocks for market-regime and signal filtering.
-- NULL allowed initially so the migration is backward-compatible with existing rows;
-- StockSeedService / seed updates will populate it via the ApplicationRunner.

ALTER TABLE stocks
    ADD COLUMN sector VARCHAR(50);

COMMENT ON COLUMN stocks.sector IS 'NSE sector classification (e.g. IT, Banking, Pharma)';
