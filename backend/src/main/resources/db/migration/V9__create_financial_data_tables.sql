CREATE TABLE dart_corp_codes (
    corp_code VARCHAR(20) PRIMARY KEY,
    corp_name VARCHAR(200) NOT NULL,
    stock_code VARCHAR(20),
    modify_date VARCHAR(20),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX idx_dart_corp_codes_stock_code ON dart_corp_codes (stock_code);

CREATE TABLE financial_metrics (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    year INTEGER NOT NULL,
    quarter INTEGER NOT NULL,
    revenue NUMERIC(24, 2),
    operating_income NUMERIC(24, 2),
    net_income NUMERIC(24, 2),
    total_debt NUMERIC(24, 2),
    total_equity NUMERIC(24, 2),
    cash NUMERIC(24, 2),
    operating_cash_flow NUMERIC(24, 2),
    interest_expense NUMERIC(24, 2),
    debt_ratio NUMERIC(20, 6),
    interest_coverage_ratio NUMERIC(20, 6),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_metrics_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_financial_metrics_asset_period UNIQUE (asset_id, year, quarter),
    CONSTRAINT ck_financial_metrics_quarter CHECK (quarter BETWEEN 1 AND 4)
);

CREATE INDEX idx_financial_metrics_asset_period ON financial_metrics (asset_id, year DESC, quarter DESC);

CREATE TABLE debt_maturities (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    maturity_date DATE NOT NULL,
    amount NUMERIC(24, 2) NOT NULL,
    debt_type VARCHAR(40) NOT NULL,
    interest_rate NUMERIC(10, 4),
    currency VARCHAR(10) NOT NULL,
    is_short_term BOOLEAN NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_debt_maturities_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_debt_maturities_sample UNIQUE (asset_id, maturity_date, amount, debt_type)
);

CREATE INDEX idx_debt_maturities_asset_date ON debt_maturities (asset_id, maturity_date);

CREATE TABLE financial_collection_logs (
    job_id UUID PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    corp_code VARCHAR(20),
    year INTEGER NOT NULL,
    quarter INTEGER NOT NULL,
    statement_division VARCHAR(10),
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    raw_s3_path VARCHAR(1000),
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_collection_logs_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT fk_financial_collection_logs_user FOREIGN KEY (requested_by_user_id) REFERENCES app_users (id),
    CONSTRAINT ck_financial_collection_logs_quarter CHECK (quarter BETWEEN 1 AND 4)
);

CREATE INDEX idx_financial_collection_logs_user_created ON financial_collection_logs (requested_by_user_id, created_at DESC);
CREATE INDEX idx_financial_collection_logs_asset_created ON financial_collection_logs (asset_id, created_at DESC);
