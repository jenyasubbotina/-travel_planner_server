CREATE TABLE trip_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    is_group BOOLEAN NOT NULL DEFAULT false,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_checklist_trip ON trip_checklist_items(trip_id);
CREATE INDEX idx_checklist_owner ON trip_checklist_items(owner_user_id);

CREATE TABLE trip_checklist_completions (
    item_id UUID NOT NULL REFERENCES trip_checklist_items(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (item_id, user_id)
);
CREATE INDEX idx_checklist_completions_user ON trip_checklist_completions(user_id);
