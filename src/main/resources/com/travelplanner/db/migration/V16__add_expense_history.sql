-- Audit trail of expense state at every version transition.
-- Enables "Cancel both" conflict resolution by reverting an expense to the
-- snapshot at expense_pending_updates.base_version (i.e., the state the
-- losing client was editing from before the winner's update was applied).
CREATE TABLE expense_history (
    expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    version BIGINT NOT NULL,
    snapshot TEXT NOT NULL,
    edited_by_user_id UUID NOT NULL REFERENCES users(id),
    edited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (expense_id, version)
);

CREATE INDEX idx_expense_history_expense ON expense_history(expense_id);

-- Backfill: capture current state of every existing expense at its current
-- version so future "Cancel both" actions targeting the present have something
-- to restore. Splits are intentionally omitted from the backfill snapshot —
-- the JSON is opaque text and the consumer treats missing fields as
-- "no change". Newly-recorded snapshots will include splits.
INSERT INTO expense_history (expense_id, version, snapshot, edited_by_user_id, edited_at)
SELECT
    e.id,
    e.version,
    json_build_object(
        'title', e.title,
        'description', e.description,
        'amount', e.amount::text,
        'currency', e.currency,
        'category', e.category,
        'expenseDate', e.expense_date::text,
        'splitType', e.split_type,
        'payerUserId', e.payer_user_id::text
    )::text,
    e.created_by,
    e.updated_at
FROM expenses e
WHERE e.deleted_at IS NULL
ON CONFLICT DO NOTHING;
