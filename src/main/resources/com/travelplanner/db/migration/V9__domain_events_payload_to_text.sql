ALTER TABLE domain_events
    ALTER COLUMN payload TYPE TEXT
    USING payload::text;
