ALTER TABLE stations
    ADD COLUMN security_profile integer NOT NULL DEFAULT 1 CHECK (security_profile BETWEEN 0 AND 3),
    ADD COLUMN requested_security_profile integer CHECK (requested_security_profile BETWEEN 0 AND 3),
    ADD COLUMN connection_security_profile integer CHECK (connection_security_profile BETWEEN 0 AND 3),
    ADD COLUMN pinned_certificate_fingerprint varchar(64),
    ADD COLUMN certificate_subject varchar(500),
    ADD COLUMN certificate_fingerprint varchar(64),
    ADD COLUMN certificate_expires_at timestamptz,
    ADD COLUMN issued_certificate_fingerprint varchar(64),
    ADD COLUMN pending_secret_hash text,
    ADD COLUMN heartbeat_interval integer CHECK (heartbeat_interval BETWEEN 10 AND 300),
    ADD COLUMN data_transfer_vendors varchar(1000) NOT NULL DEFAULT '',
    ADD COLUMN log_status varchar(40);

ALTER TABLE ocpp_calls
    ADD COLUMN connection_id varchar(64) NOT NULL DEFAULT '';

ALTER TABLE charging_transactions
    ADD COLUMN signed_meter_start text,
    ADD COLUMN signed_meter_stop text,
    ADD COLUMN signed_meter_format varchar(20);
