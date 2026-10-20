ALTER TABLE stations
    ADD COLUMN auto_charge boolean NOT NULL DEFAULT false;

ALTER TABLE connectors
    ADD COLUMN plugged_at timestamptz,
    ADD COLUMN vid varchar(20);

ALTER TABLE charging_tokens
    ADD COLUMN vehicle_id uuid;
CREATE INDEX charging_tokens_vehicle ON charging_tokens(tenant_id, vehicle_id);

ALTER TABLE charging_transactions
    ADD COLUMN vid varchar(20);
