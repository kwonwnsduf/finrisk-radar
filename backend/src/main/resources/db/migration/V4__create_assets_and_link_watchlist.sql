CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    ticker VARCHAR(100) NOT NULL,
    market VARCHAR(50),
    sector VARCHAR(100),
    country VARCHAR(10),
    currency VARCHAR(10),
    asset_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_assets_ticker_market UNIQUE NULLS NOT DISTINCT (ticker, market)
);

CREATE INDEX idx_assets_name ON assets (name);
CREATE INDEX idx_assets_asset_type ON assets (asset_type);

INSERT INTO assets (name, ticker, market, asset_type, created_at, updated_at)
SELECT min(asset_code), normalized_code, 'LEGACY', 'STOCK', min(created_at), min(created_at)
FROM (
    SELECT asset_code, upper(trim(asset_code)) AS normalized_code, created_at
    FROM watchlist_items
) legacy_codes
GROUP BY normalized_code;

ALTER TABLE watchlist_items ADD COLUMN asset_id BIGINT;

UPDATE watchlist_items watchlist
SET asset_id = asset.id
FROM assets asset
WHERE asset.ticker = upper(trim(watchlist.asset_code))
  AND asset.market = 'LEGACY';

DELETE FROM watchlist_items duplicate
USING watchlist_items keeper
WHERE duplicate.user_id = keeper.user_id
  AND duplicate.asset_id = keeper.asset_id
  AND duplicate.id > keeper.id;

ALTER TABLE watchlist_items
    DROP CONSTRAINT uk_watchlist_items_user_asset_code,
    ALTER COLUMN asset_id SET NOT NULL,
    ADD CONSTRAINT fk_watchlist_items_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id),
    ADD CONSTRAINT uk_watchlist_items_user_asset UNIQUE (user_id, asset_id),
    DROP COLUMN asset_code;

CREATE INDEX idx_watchlist_items_asset_id ON watchlist_items (asset_id);
