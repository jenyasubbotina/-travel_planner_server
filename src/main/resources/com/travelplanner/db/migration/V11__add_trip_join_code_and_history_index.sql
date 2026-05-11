ALTER TABLE trips ADD COLUMN join_code VARCHAR(8);
UPDATE trips SET join_code = upper(substr(md5(random()::text || id::text), 1, 8));
ALTER TABLE trips ALTER COLUMN join_code SET NOT NULL;
CREATE UNIQUE INDEX idx_trips_join_code ON trips(join_code);

CREATE INDEX idx_domain_events_aggregate_created
    ON domain_events(aggregate_id, created_at DESC);
