CREATE TABLE collection_logs (
    job_id UUID PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    ticker VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    source VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    raw_s3_path VARCHAR(1000),
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_collection_logs_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT fk_collection_logs_user FOREIGN KEY (requested_by_user_id) REFERENCES app_users (id),
    CONSTRAINT ck_collection_logs_date_range CHECK (start_date <= end_date)
);

CREATE INDEX idx_collection_logs_user_created ON collection_logs (requested_by_user_id, created_at DESC);
CREATE INDEX idx_collection_logs_asset_created ON collection_logs (asset_id, created_at DESC);
