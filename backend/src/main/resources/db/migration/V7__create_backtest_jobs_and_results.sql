CREATE TABLE backtest_jobs (
    job_id UUID PRIMARY KEY,
    requested_by_user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    strategy_type VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_backtest_jobs_user FOREIGN KEY (requested_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_backtest_jobs_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_backtest_jobs_date_range CHECK (start_date <= end_date)
);

CREATE INDEX idx_backtest_jobs_user_created
    ON backtest_jobs (requested_by_user_id, created_at DESC);

CREATE TABLE backtest_results (
    job_id UUID PRIMARY KEY,
    first_price_date DATE NOT NULL,
    last_price_date DATE NOT NULL,
    initial_close NUMERIC(20, 6) NOT NULL,
    final_close NUMERIC(20, 6) NOT NULL,
    total_return_rate NUMERIC(20, 6) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_backtest_results_job FOREIGN KEY (job_id) REFERENCES backtest_jobs (job_id)
);
