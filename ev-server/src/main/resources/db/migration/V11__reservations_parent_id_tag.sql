-- EV Supervision Lite — built by Kevin Vavelin, Codetics
-- https://codetics.fr
-- Copyright (c) 2026 Kevin Vavelin, Codetics
-- SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

ALTER TABLE charging_tokens
    ADD COLUMN parent_id_tag varchar(20);

CREATE TABLE station_reservations (
    tenant_id uuid NOT NULL,
    station_id varchar(100) NOT NULL,
    reservation_id integer NOT NULL CHECK (reservation_id >= 0),
    connector_id integer NOT NULL CHECK (connector_id >= 0),
    id_tag varchar(20) NOT NULL,
    parent_id_tag varchar(20),
    expiry_date timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    transaction_id integer,
    command_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, station_id, reservation_id),
    FOREIGN KEY (tenant_id, station_id) REFERENCES stations(tenant_id, id)
);
CREATE INDEX station_reservations_recent ON station_reservations(tenant_id, created_at DESC);
