CREATE TABLE expense_pending_updates (
    expense_id UUID PRIMARY KEY REFERENCES expenses(id) ON DELETE CASCADE,
    proposed_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proposed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    base_version BIGINT NOT NULL,
    payload TEXT NOT NULL
);
CREATE INDEX idx_expense_pending_updates_proposer ON expense_pending_updates(proposed_by_user_id);
