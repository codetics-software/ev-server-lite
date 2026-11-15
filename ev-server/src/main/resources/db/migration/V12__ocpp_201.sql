-- EV Supervision Lite — built by Kevin Vavelin, Codetics
-- https://codetics.fr
-- Copyright (c) 2026 Kevin Vavelin, Codetics
-- SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

ALTER TABLE stations
    ADD COLUMN ocpp_protocol varchar(10) NOT NULL DEFAULT '1.6'
        CHECK (ocpp_protocol IN ('AUTO', '1.6', '2.0.1')),
    ADD COLUMN v2g_certificate_subject varchar(500),
    ADD COLUMN v2g_certificate_expires_at timestamptz;

ALTER TABLE connectors
    ADD COLUMN ocpp_connectors text;

ALTER TABLE charging_transactions
    ADD COLUMN ocpp_transaction_id varchar(36);
CREATE UNIQUE INDEX transactions_ocpp_transaction ON charging_transactions(tenant_id, station_id, ocpp_transaction_id)
    WHERE ocpp_transaction_id IS NOT NULL;

ALTER TABLE station_commands
    ADD COLUMN ocpp_version varchar(10),
    ADD COLUMN wire_version varchar(10);

ALTER TABLE station_parameters
    ALTER COLUMN parameter_key TYPE varchar(255);
