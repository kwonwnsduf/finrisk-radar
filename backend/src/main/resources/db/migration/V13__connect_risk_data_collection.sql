ALTER TABLE assets ADD COLUMN dart_corp_code VARCHAR(20);

UPDATE assets
SET dart_corp_code = '00922702'
WHERE ticker = 'JTBC' AND market = 'PRIVATE';

CREATE UNIQUE INDEX uq_assets_dart_corp_code
    ON assets(dart_corp_code)
    WHERE dart_corp_code IS NOT NULL;

ALTER TABLE financial_collection_logs
    ADD COLUMN risk_calculation_job_id UUID REFERENCES risk_calculation_jobs(job_id);

CREATE INDEX idx_financial_collection_logs_risk_job
    ON financial_collection_logs(risk_calculation_job_id)
    WHERE risk_calculation_job_id IS NOT NULL;

ALTER TABLE risk_calculation_jobs DROP CONSTRAINT ck_risk_job_status;
ALTER TABLE risk_calculation_jobs
    ADD CONSTRAINT ck_risk_job_status
    CHECK(status IN ('COLLECTING', 'REQUESTED', 'RUNNING', 'COMPLETED', 'FAILED'));

DROP INDEX uq_risk_active_job_asset;
CREATE UNIQUE INDEX uq_risk_active_job_asset
    ON risk_calculation_jobs(asset_id)
    WHERE status IN ('COLLECTING', 'REQUESTED', 'RUNNING');
