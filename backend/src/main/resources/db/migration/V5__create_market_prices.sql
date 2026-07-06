CREATE TABLE market_prices (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    date DATE NOT NULL,
    open NUMERIC(20, 6) NOT NULL,
    high NUMERIC(20, 6) NOT NULL,
    low NUMERIC(20, 6) NOT NULL,
    close NUMERIC(20, 6) NOT NULL,
    volume BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_market_prices_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_market_prices_asset_date_source UNIQUE (asset_id, date, source),
    CONSTRAINT ck_market_prices_non_negative CHECK (
        open >= 0 AND high >= 0 AND low >= 0 AND close >= 0 AND volume >= 0
    )
);

CREATE INDEX idx_market_prices_asset_date ON market_prices (asset_id, date);
