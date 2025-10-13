-- Add client_uuid to review_log for idempotent uploads and create unique index
ALTER TABLE review_log ADD COLUMN IF NOT EXISTS client_uuid VARCHAR(64);

-- Unique index on client_uuid allows multiple NULLs but ensures UUID uniqueness
CREATE UNIQUE INDEX IF NOT EXISTS ux_review_log_client_uuid ON review_log(client_uuid);

