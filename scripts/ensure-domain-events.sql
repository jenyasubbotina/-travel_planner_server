CREATE TABLE IF NOT EXISTS domain_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_domain_events_unprocessed ON domain_events (created_at) WHERE processed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_domain_events_aggregate ON domain_events (aggregate_type, aggregate_id);
