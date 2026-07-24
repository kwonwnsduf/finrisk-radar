CREATE TABLE ai_reports (
  id UUID PRIMARY KEY,
  requested_by_user_id BIGINT NOT NULL REFERENCES app_users(id),
  asset_id BIGINT REFERENCES assets(id),
  backtest_job_id UUID REFERENCES backtest_jobs(job_id),
  report_type VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  current_step VARCHAR(30),
  question VARCHAR(500),
  title VARCHAR(300),
  content TEXT,
  structured_result JSONB,
  model VARCHAR(100),
  prompt_version VARCHAR(100) NOT NULL,
  input_token_count BIGINT NOT NULL DEFAULT 0,
  output_token_count BIGINT NOT NULL DEFAULT 0,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  failure_code VARCHAR(100),
  failure_message VARCHAR(1000),
  retryable_failure BOOLEAN,
  request_fingerprint VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(100),
  usage_type VARCHAR(30) NOT NULL,
  usage_reservation_key VARCHAR(200) NOT NULL,
  usage_compensated_at TIMESTAMP,
  requested_at TIMESTAMP NOT NULL,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  failed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT ck_ai_reports_type CHECK (report_type IN ('RISK_ANALYSIS','BACKTEST_ANALYSIS','WATCHLIST_SUMMARY')),
  CONSTRAINT ck_ai_reports_status CHECK (status IN ('REQUESTED','RUNNING','COMPLETED','FAILED')),
  CONSTRAINT ck_ai_reports_target CHECK (
    (report_type = 'RISK_ANALYSIS' AND asset_id IS NOT NULL AND backtest_job_id IS NULL) OR
    (report_type = 'BACKTEST_ANALYSIS' AND asset_id IS NULL AND backtest_job_id IS NOT NULL) OR
    (report_type = 'WATCHLIST_SUMMARY' AND asset_id IS NULL AND backtest_job_id IS NULL)
  )
);

CREATE UNIQUE INDEX uq_ai_reports_user_idempotency
  ON ai_reports(requested_by_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_ai_reports_user_requested
  ON ai_reports(requested_by_user_id, requested_at DESC);
CREATE INDEX idx_ai_reports_status_requested
  ON ai_reports(status, requested_at);
CREATE INDEX idx_ai_reports_fingerprint
  ON ai_reports(requested_by_user_id, report_type, request_fingerprint, requested_at DESC);

ALTER TABLE backtest_jobs ADD COLUMN natural_language_question VARCHAR(500);
ALTER TABLE backtest_jobs ADD COLUMN parser_model VARCHAR(100);
ALTER TABLE backtest_jobs ADD COLUMN parser_prompt_version VARCHAR(100);
ALTER TABLE backtest_jobs ADD COLUMN parser_input_token_count BIGINT;
ALTER TABLE backtest_jobs ADD COLUMN parser_output_token_count BIGINT;
