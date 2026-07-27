CREATE TABLE payment_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    product_code VARCHAR(40) NOT NULL,
    order_name VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    customer_key VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    confirming_at TIMESTAMP WITHOUT TIME ZONE,
    paid_at TIMESTAMP WITHOUT TIME ZONE,
    canceled_at TIMESTAMP WITHOUT TIME ZONE,
    failed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_orders_order_id UNIQUE (order_id),
    CONSTRAINT ck_payment_orders_product CHECK (product_code = 'PREMIUM_MONTHLY'),
    CONSTRAINT ck_payment_orders_amount CHECK (amount = 5900),
    CONSTRAINT ck_payment_orders_currency CHECK (currency = 'KRW'),
    CONSTRAINT ck_payment_orders_provider CHECK (provider = 'TOSS'),
    CONSTRAINT ck_payment_orders_status CHECK (
        status IN ('READY','CONFIRMING','PAID','CANCELING','CANCELED','FAILED','RECOVERY_REQUIRED')
    )
);
CREATE INDEX idx_payment_orders_user_created ON payment_orders(user_id, created_at DESC);
CREATE INDEX idx_payment_orders_recovery ON payment_orders(status, updated_at);

CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_order_id BIGINT NOT NULL REFERENCES payment_orders(id),
    payment_key VARCHAR(200) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_status VARCHAR(30) NOT NULL,
    method VARCHAR(50),
    total_amount BIGINT NOT NULL,
    supplied_amount BIGINT,
    approved_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    receipt_url VARCHAR(1000),
    raw_response JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uq_payment_transactions_order UNIQUE (payment_order_id),
    CONSTRAINT uq_payment_transactions_key UNIQUE (payment_key),
    CONSTRAINT ck_payment_transactions_provider CHECK (provider = 'TOSS'),
    CONSTRAINT ck_payment_transactions_amount CHECK (total_amount = 5900)
);

CREATE TABLE payment_attempts (
    id BIGSERIAL PRIMARY KEY,
    payment_order_id BIGINT REFERENCES payment_orders(id),
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    request_id UUID NOT NULL,
    idempotency_key UUID,
    attempt_type VARCHAR(30) NOT NULL,
    result VARCHAR(30) NOT NULL,
    request_fingerprint VARCHAR(64),
    response_payload JSONB,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    client_ip VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_payment_attempts_request UNIQUE (request_id),
    CONSTRAINT ck_payment_attempts_type CHECK (
        attempt_type IN ('ORDER_CREATE','CONFIRM','CANCEL','RECOVERY')
    ),
    CONSTRAINT ck_payment_attempts_result CHECK (
        result IN ('STARTED','SUCCEEDED','FAILED','BLOCKED','DUPLICATE','RECOVERY_REQUIRED')
    )
);
CREATE UNIQUE INDEX uq_payment_attempts_idempotency
    ON payment_attempts(user_id, attempt_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payment_attempts_user_created ON payment_attempts(user_id, created_at DESC);
CREATE INDEX idx_payment_attempts_ip_created ON payment_attempts(client_ip, created_at DESC);

CREATE TABLE payment_cancellations (
    id BIGSERIAL PRIMARY KEY,
    payment_order_id BIGINT NOT NULL REFERENCES payment_orders(id),
    cancel_request_id UUID NOT NULL,
    cancel_reason VARCHAR(200) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_response JSONB,
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    failed_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_payment_cancellations_request UNIQUE (cancel_request_id),
    CONSTRAINT uq_payment_cancellations_order UNIQUE (payment_order_id),
    CONSTRAINT ck_payment_cancellations_amount CHECK (amount = 5900),
    CONSTRAINT ck_payment_cancellations_status CHECK (
        status IN ('REQUESTED','PROCESSING','COMPLETED','FAILED')
    )
);

CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    plan VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMP WITHOUT TIME ZONE,
    current_period_end TIMESTAMP WITHOUT TIME ZONE,
    activated_by_payment_order_id BIGINT REFERENCES payment_orders(id),
    canceled_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_subscriptions_user UNIQUE (user_id),
    CONSTRAINT ck_subscriptions_plan CHECK (plan IN ('FREE','PREMIUM')),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('ACTIVE','CANCELED','EXPIRED'))
);

CREATE TABLE subscription_entitlements (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    payment_order_id BIGINT NOT NULL REFERENCES payment_orders(id),
    original_duration_seconds BIGINT NOT NULL DEFAULT 2592000,
    period_start TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    period_end TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_until TIMESTAMP WITHOUT TIME ZONE,
    status VARCHAR(20) NOT NULL,
    canceled_at TIMESTAMP WITHOUT TIME ZONE,
    removed_unused_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_subscription_entitlements_order UNIQUE (payment_order_id),
    CONSTRAINT ck_subscription_entitlements_duration CHECK (original_duration_seconds = 2592000),
    CONSTRAINT ck_subscription_entitlements_period CHECK (period_end >= period_start),
    CONSTRAINT ck_subscription_entitlements_removed CHECK (removed_unused_seconds >= 0),
    CONSTRAINT ck_subscription_entitlements_status CHECK (
        status IN ('SCHEDULED','ACTIVE','CONSUMED','CANCELED')
    )
);
CREATE INDEX idx_subscription_entitlements_user_period
    ON subscription_entitlements(user_id, period_start, period_end);

CREATE TABLE fsd_events (
    id BIGSERIAL PRIMARY KEY,
    payment_order_id BIGINT REFERENCES payment_orders(id),
    payment_attempt_id BIGINT REFERENCES payment_attempts(id),
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    rule_code VARCHAR(80) NOT NULL,
    phase VARCHAR(30) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL,
    reason VARCHAR(500) NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(30) NOT NULL,
    detected_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITHOUT TIME ZONE,
    reviewed_by BIGINT REFERENCES app_users(id),
    review_note VARCHAR(1000),
    CONSTRAINT ck_fsd_events_phase CHECK (phase IN ('PRE_CONFIRM','POST_PAYMENT','POST_CANCEL')),
    CONSTRAINT ck_fsd_events_decision CHECK (decision IN ('ALLOW','REVIEW','BLOCK')),
    CONSTRAINT ck_fsd_events_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_fsd_events_status CHECK (status IN ('OPEN','REVIEWING','RESOLVED','FALSE_POSITIVE')),
    CONSTRAINT ck_fsd_events_score CHECK (score BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX uq_fsd_events_order_rule_phase
    ON fsd_events(payment_order_id, rule_code, phase)
    WHERE payment_order_id IS NOT NULL;
CREATE INDEX idx_fsd_events_search ON fsd_events(status, severity, decision, detected_at DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at TIMESTAMP WITHOUT TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    CONSTRAINT uq_outbox_events_key UNIQUE (event_key),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX idx_outbox_events_publish ON outbox_events(status, occurred_at);

CREATE TABLE consumed_events (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    consumed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uq_consumed_events_consumer_event UNIQUE (consumer_name, event_id)
);
