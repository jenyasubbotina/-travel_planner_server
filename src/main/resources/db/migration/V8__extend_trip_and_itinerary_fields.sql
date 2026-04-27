ALTER TABLE trips ADD COLUMN total_budget NUMERIC(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN destination VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE trips ADD COLUMN image_url TEXT;

ALTER TABLE itinerary_points ADD COLUMN day_index INT NOT NULL DEFAULT 0;
ALTER TABLE itinerary_points ADD COLUMN subtitle VARCHAR(500);
ALTER TABLE itinerary_points ADD COLUMN duration VARCHAR(50);
ALTER TABLE itinerary_points ADD COLUMN cost DOUBLE PRECISION;
ALTER TABLE itinerary_points ADD COLUMN actual_cost DOUBLE PRECISION;
ALTER TABLE itinerary_points ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NONE';

CREATE TABLE itinerary_point_participants (
    point_id UUID NOT NULL REFERENCES itinerary_points(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (point_id, user_id)
);

CREATE INDEX idx_itinerary_point_participants_point_id ON itinerary_point_participants(point_id);
CREATE INDEX idx_itinerary_point_participants_user_id ON itinerary_point_participants(user_id);
