ALTER TABLE trip_participants ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE trip_participants ADD COLUMN deleted_at TIMESTAMPTZ NULL;
ALTER TABLE trip_participants ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE trip_participants SET updated_at = joined_at WHERE updated_at IS NULL;

ALTER TABLE trip_participants ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE trip_participants ALTER COLUMN updated_at SET DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_trip_participants_updated_at ON trip_participants(updated_at);
