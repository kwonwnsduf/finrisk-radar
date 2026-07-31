CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    target_url VARCHAR(500),
    event_id VARCHAR(100) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    read_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_notifications_event_user UNIQUE (event_id, user_id),
    CONSTRAINT ck_notifications_type CHECK (
        type IN (
            'BACKTEST_COMPLETED',
            'BACKTEST_FAILED',
            'REPORT_COMPLETED',
            'REPORT_FAILED',
            'HIGH_RISK_SIGNAL_DETECTED',
            'PAYMENT_COMPLETED',
            'PAYMENT_CANCELED',
            'PAYMENT_FAILED',
            'FSD_REVIEW_REQUIRED',
            'PAYMENT_RECOVERY_REQUIRED'
        )
    ),
    CONSTRAINT ck_notifications_reference_type CHECK (
        reference_type IN ('BACKTEST', 'AI_REPORT', 'ASSET', 'PAYMENT_ORDER', 'FSD_EVENT')
    ),
    CONSTRAINT ck_notifications_target_url CHECK (
        target_url IS NULL OR target_url LIKE '/%'
    )
);

CREATE INDEX idx_notifications_user_created
    ON notifications(user_id, created_at DESC, id DESC);

CREATE INDEX idx_notifications_user_read_created
    ON notifications(user_id, is_read, created_at DESC, id DESC);
