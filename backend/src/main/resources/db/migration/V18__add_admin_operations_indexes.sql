CREATE INDEX idx_payment_orders_status_created
    ON payment_orders(status, created_at DESC);

CREATE INDEX idx_payment_transactions_approved
    ON payment_transactions(approved_at DESC);

CREATE INDEX idx_payment_attempts_result_completed
    ON payment_attempts(result, completed_at DESC);

CREATE INDEX idx_payment_cancellations_status_completed
    ON payment_cancellations(status, completed_at DESC);

CREATE INDEX idx_subscriptions_status_period_end
    ON subscriptions(status, current_period_end);

CREATE INDEX idx_credit_event_candidates_status_created
    ON credit_event_candidates(status, created_at DESC);

CREATE INDEX idx_backtest_jobs_status_completed
    ON backtest_jobs(status, completed_at DESC);

CREATE INDEX idx_collection_logs_status_completed
    ON collection_logs(status, completed_at DESC);

CREATE INDEX idx_document_collection_jobs_status_completed
    ON document_collection_jobs(status, completed_at DESC);
