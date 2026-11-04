-- EV Supervision Lite — built by Kevin Vavelin, Codetics
-- https://codetics.fr
-- Copyright (c) 2026 Kevin Vavelin, Codetics
-- SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

ALTER TABLE workspace_payment_settings
    ADD COLUMN card_sale_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN card_price_minor bigint CHECK (card_price_minor > 0),
    ADD COLUMN card_description varchar(500),
    ADD COLUMN card_shipping boolean NOT NULL DEFAULT true;

CREATE TABLE card_orders (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    user_id uuid NOT NULL,
    request_id uuid NOT NULL,
    state varchar(30) NOT NULL,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency varchar(3) NOT NULL,
    recipient_name varchar(200),
    address varchar(200),
    postal_code varchar(20),
    city varchar(200),
    stripe_account_id varchar(255) NOT NULL,
    stripe_checkout_id varchar(255),
    stripe_payment_intent_id varchar(255),
    checkout_url text,
    refund_id varchar(255),
    token_uid varchar(100),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    paid_at timestamptz,
    shipped_at timestamptz,
    activated_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, request_id)
);
CREATE INDEX card_orders_user ON card_orders(tenant_id, user_id, created_at DESC);
CREATE INDEX card_orders_state ON card_orders(tenant_id, state, created_at DESC);
