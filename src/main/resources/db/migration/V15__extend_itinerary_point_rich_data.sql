ALTER TABLE itinerary_points ADD COLUMN category VARCHAR(50);

CREATE TABLE itinerary_point_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    point_id UUID NOT NULL REFERENCES itinerary_points(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_point_links_point ON itinerary_point_links(point_id);

CREATE TABLE itinerary_point_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    point_id UUID NOT NULL REFERENCES itinerary_points(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_point_comments_point_created ON itinerary_point_comments(point_id, created_at);

ALTER TABLE attachments ADD COLUMN point_id UUID REFERENCES itinerary_points(id) ON DELETE CASCADE;
CREATE INDEX idx_attachments_point ON attachments(point_id);
