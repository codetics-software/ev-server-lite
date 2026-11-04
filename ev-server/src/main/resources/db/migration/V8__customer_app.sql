-- EV Supervision Lite — built by Kevin Vavelin, Codetics
-- https://codetics.fr
-- Copyright (c) 2026 Kevin Vavelin, Codetics
-- SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

CREATE TABLE push_devices (
    token varchar(200) PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    user_id uuid NOT NULL,
    platform varchar(20) NOT NULL,
    environment varchar(20) NOT NULL,
    locale varchar(20),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX push_devices_user ON push_devices(tenant_id, user_id);

CREATE TABLE push_outbox (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    user_id uuid NOT NULL,
    type varchar(60) NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    transaction_id integer,
    attempts integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz
);
CREATE INDEX push_outbox_pending ON push_outbox(sent_at, attempts, created_at);
CREATE INDEX push_outbox_user ON push_outbox(user_id);

CREATE TABLE station_photos (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    station_id varchar(100) NOT NULL,
    user_id uuid NOT NULL,
    content_type varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX station_photos_station ON station_photos(tenant_id, station_id, created_at DESC);
CREATE INDEX station_photos_user ON station_photos(tenant_id, user_id);

CREATE TABLE station_photo_contents (
    photo_id uuid PRIMARY KEY REFERENCES station_photos(id) ON DELETE CASCADE,
    content bytea NOT NULL
);
