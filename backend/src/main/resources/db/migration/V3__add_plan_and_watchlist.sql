ALTER TABLE app_users
    ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'FREE';

CREATE TABLE watchlist_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_code VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_watchlist_items_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT uk_watchlist_items_user_asset_code UNIQUE (user_id, asset_code)
);

CREATE INDEX idx_watchlist_items_user_id ON watchlist_items (user_id);

-- Day4 migration: add nullable asset_id, backfill from asset_code, then replace
-- the current unique constraint with (user_id, asset_id).
