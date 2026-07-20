ALTER TABLE credit_event_candidates
  ADD COLUMN recalculation_attempt_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN recalculation_last_attempted_at TIMESTAMP,
  ADD COLUMN recalculation_last_error VARCHAR(1000);

