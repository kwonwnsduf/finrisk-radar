ALTER TABLE app_users
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN provider_id VARCHAR(255);

ALTER TABLE app_users
    ADD CONSTRAINT uk_app_users_provider_provider_id UNIQUE (provider, provider_id);
