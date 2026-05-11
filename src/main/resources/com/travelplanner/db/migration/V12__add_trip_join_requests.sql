CREATE TABLE trip_join_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    requester_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX idx_trip_join_requests_pending
    ON trip_join_requests(trip_id, requester_user_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_trip_join_requests_trip ON trip_join_requests(trip_id);
