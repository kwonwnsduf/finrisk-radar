ALTER TABLE financial_metrics ADD COLUMN period_end_date DATE;
ALTER TABLE financial_metrics ADD COLUMN report_code VARCHAR(10);
ALTER TABLE financial_metrics ADD COLUMN flow_basis VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE financial_metrics ADD COLUMN fetched_at TIMESTAMP WITHOUT TIME ZONE;
CREATE INDEX idx_financial_metrics_asset_end_date ON financial_metrics(asset_id, period_end_date DESC);

ALTER TABLE debt_maturities ADD COLUMN snapshot_date DATE;
ALTER TABLE debt_maturities ADD COLUMN external_debt_key VARCHAR(200);
UPDATE debt_maturities SET snapshot_date = created_at::date WHERE snapshot_date IS NULL;
CREATE INDEX idx_debt_maturities_asset_snapshot ON debt_maturities(asset_id, snapshot_date DESC, maturity_date);

ALTER TABLE assets ADD COLUMN market_price_asset_id BIGINT REFERENCES assets(id);
ALTER TABLE assets ADD CONSTRAINT ck_assets_market_proxy_distinct CHECK(market_price_asset_id IS NULL OR market_price_asset_id <> id);

CREATE TABLE risk_calculation_jobs (
 job_id UUID PRIMARY KEY, requested_by_user_id BIGINT NOT NULL REFERENCES app_users(id), asset_id BIGINT NOT NULL REFERENCES assets(id),
 status VARCHAR(20) NOT NULL, requested_at TIMESTAMP NOT NULL, started_at TIMESTAMP, completed_at TIMESTAMP, failed_at TIMESTAMP,
 failure_code VARCHAR(100), failure_message VARCHAR(1000), rule_version VARCHAR(100) NOT NULL, data_as_of_date DATE NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT ck_risk_job_status CHECK(status IN ('REQUESTED','RUNNING','COMPLETED','FAILED'))
);
CREATE UNIQUE INDEX uq_risk_active_job_asset ON risk_calculation_jobs(asset_id) WHERE status IN ('REQUESTED','RUNNING');
CREATE INDEX idx_risk_jobs_user_requested ON risk_calculation_jobs(requested_by_user_id, requested_at DESC);

CREATE TABLE risk_scores (
 id BIGSERIAL PRIMARY KEY, job_id UUID NOT NULL UNIQUE REFERENCES risk_calculation_jobs(job_id), asset_id BIGINT NOT NULL REFERENCES assets(id),
 total_score INTEGER NOT NULL CHECK(total_score BETWEEN 0 AND 100), risk_grade VARCHAR(20) NOT NULL, default_status VARCHAR(30) NOT NULL,
 financial_score INTEGER, liquidity_score INTEGER, market_score INTEGER, credit_event_score INTEGER, group_contagion_score INTEGER,
 category_statuses JSONB NOT NULL, data_quality VARCHAR(30) NOT NULL, confidence VARCHAR(20) NOT NULL,
 required_rule_success_rate INTEGER NOT NULL, missing_categories JSONB NOT NULL,
 used_financial_metric_count INTEGER NOT NULL, used_debt_maturity_count INTEGER NOT NULL, used_market_price_count INTEGER NOT NULL,
 used_credit_event_count INTEGER NOT NULL, used_relationship_count INTEGER NOT NULL,
 data_as_of_date DATE NOT NULL, rule_version VARCHAR(100) NOT NULL, calculated_at TIMESTAMP NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_risk_scores_asset_calculated ON risk_scores(asset_id, calculated_at DESC, id DESC);

CREATE TABLE credit_events (
 id BIGSERIAL PRIMARY KEY, asset_id BIGINT NOT NULL REFERENCES assets(id), event_type VARCHAR(50) NOT NULL, event_date DATE NOT NULL,
 severity VARCHAR(20) NOT NULL, source_type VARCHAR(30) NOT NULL, source_name VARCHAR(200), source_document_id VARCHAR(200),
 description VARCHAR(2000), incident_key VARCHAR(200), external_event_key VARCHAR(200) NOT NULL UNIQUE,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_credit_events_asset_date ON credit_events(asset_id, event_date DESC);

CREATE TABLE asset_relationships (
 id BIGSERIAL PRIMARY KEY, from_asset_id BIGINT NOT NULL REFERENCES assets(id), to_asset_id BIGINT NOT NULL REFERENCES assets(id),
 relationship_type VARCHAR(50) NOT NULL, exposure_amount NUMERIC(24,2), currency VARCHAR(10), effective_from DATE NOT NULL,
 effective_to DATE, description VARCHAR(1000), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT ck_asset_relationship_distinct CHECK(from_asset_id <> to_asset_id),
 CONSTRAINT ck_asset_relationship_dates CHECK(effective_to IS NULL OR effective_to >= effective_from),
 CONSTRAINT uq_asset_relationship UNIQUE(from_asset_id,to_asset_id,relationship_type,effective_from)
);
CREATE INDEX idx_asset_relationships_from ON asset_relationships(from_asset_id);
CREATE INDEX idx_asset_relationships_to ON asset_relationships(to_asset_id);

CREATE TABLE risk_signals (
 id BIGSERIAL PRIMARY KEY, risk_score_id BIGINT NOT NULL REFERENCES risk_scores(id), asset_id BIGINT NOT NULL REFERENCES assets(id),
 category VARCHAR(40) NOT NULL, rule_type VARCHAR(60) NOT NULL, signal_type VARCHAR(60) NOT NULL, severity VARCHAR(20) NOT NULL,
 score INTEGER NOT NULL, message VARCHAR(500) NOT NULL, evidence JSONB NOT NULL, source_type VARCHAR(30), source_id BIGINT,
 detected_at TIMESTAMP NOT NULL, deduplication_key VARCHAR(300) NOT NULL UNIQUE, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_risk_signals_score ON risk_signals(risk_score_id);
CREATE INDEX idx_risk_signals_asset_detected ON risk_signals(asset_id, detected_at DESC);
