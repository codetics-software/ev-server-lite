CREATE TABLE workspace_payment_settings (
    tenant_id uuid PRIMARY KEY REFERENCES tenants(id),
    stripe_account_id varchar(255) UNIQUE,
    country varchar(2) NOT NULL DEFAULT 'FR',
    currency varchar(3) NOT NULL DEFAULT 'EUR',
    enabled boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 0
);

CREATE TABLE public_evses (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    station_id varchar(100) NOT NULL,
    connector_id integer NOT NULL CHECK (connector_id > 0),
    name varchar(200) NOT NULL,
    pricing_definition_id uuid NOT NULL,
    published boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, station_id, connector_id) REFERENCES connectors(tenant_id, station_id, connector_id),
    UNIQUE (tenant_id, station_id, connector_id),
    UNIQUE (tenant_id, id)
);

CREATE TABLE charging_payments (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    evse_id uuid NOT NULL,
    station_id varchar(100) NOT NULL,
    connector_id integer NOT NULL,
    token_uid varchar(20) NOT NULL,
    access_token_hash varchar(64) NOT NULL,
    access_token_encrypted text NOT NULL,
    portal_url text NOT NULL,
    description varchar(255) NOT NULL,
    request_id uuid NOT NULL,
    stripe_account_id varchar(255) NOT NULL,
    stripe_checkout_id varchar(255),
    stripe_payment_intent_id varchar(255),
    checkout_url text,
    currency varchar(3) NOT NULL,
    prepaid_minor bigint NOT NULL CHECK (prepaid_minor > 0),
    energy_price numeric(22,6) NOT NULL,
    time_price numeric(22,6) NOT NULL,
    flat_price numeric(22,6) NOT NULL,
    state varchar(32) NOT NULL,
    transaction_id integer REFERENCES charging_transactions(id),
    start_command_id uuid REFERENCES station_commands(id),
    stop_command_id uuid REFERENCES station_commands(id),
    billed_minor bigint,
    observed_energy_wh numeric(22,6) NOT NULL DEFAULT 0,
    refunded_minor bigint NOT NULL DEFAULT 0,
    refund_id varchar(255),
    last_error text,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, evse_id) REFERENCES public_evses(tenant_id, id),
    UNIQUE (tenant_id, request_id),
    UNIQUE (tenant_id, token_uid),
    UNIQUE (stripe_account_id, stripe_checkout_id)
);
CREATE UNIQUE INDEX one_open_payment_per_evse ON charging_payments(evse_id)
WHERE state IN ('PENDING_PAYMENT', 'PAID', 'START_REQUESTED', 'CHARGING', 'STOP_REQUESTED');
CREATE INDEX charging_payments_tenant_created ON charging_payments(tenant_id, created_at DESC);

CREATE TABLE stripe_events (
    id varchar(255) PRIMARY KEY,
    account_id varchar(255) NOT NULL,
    event_type varchar(255) NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);
