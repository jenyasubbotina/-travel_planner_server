CREATE TABLE itinerary_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50),
    date DATE,
    start_time TIME,
    end_time TIME,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_itinerary_trip ON itinerary_points(trip_id);
CREATE INDEX idx_itinerary_updated ON itinerary_points(updated_at);
CREATE INDEX idx_itinerary_date ON itinerary_points(date);
