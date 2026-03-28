-- SM-34: User watchlist — stocks a user wants to receive signals for.
-- Default behaviour (empty watchlist) means the user receives alerts for all active stocks.

CREATE TABLE user_watchlist (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_watchlist PRIMARY KEY (user_id, stock_id)
);

CREATE INDEX idx_user_watchlist_user  ON user_watchlist (user_id);
CREATE INDEX idx_user_watchlist_stock ON user_watchlist (stock_id);

COMMENT ON TABLE user_watchlist IS 'Stocks a user wants to receive signals for. Default: all active stocks.';
