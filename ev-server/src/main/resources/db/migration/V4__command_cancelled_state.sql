ALTER TABLE station_commands DROP CONSTRAINT IF EXISTS station_commands_state_check;
ALTER TABLE station_commands ADD CONSTRAINT station_commands_state_check
    CHECK (state IN ('QUEUED','SENT','SUCCEEDED','FAILED','TIMED_OUT','CANCELLED'));
