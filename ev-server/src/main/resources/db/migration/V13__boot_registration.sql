-- EV Supervision Lite — built by Kevin Vavelin, Codetics
-- https://codetics.fr
-- Copyright (c) 2026 Kevin Vavelin, Codetics
-- SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

ALTER TABLE stations
    ADD COLUMN boot_policy varchar(10) NOT NULL DEFAULT 'Accepted'
        CHECK (boot_policy IN ('Accepted', 'Pending', 'Rejected')),
    ADD COLUMN registration_status varchar(10)
        CHECK (registration_status IN ('Accepted', 'Pending', 'Rejected'));
