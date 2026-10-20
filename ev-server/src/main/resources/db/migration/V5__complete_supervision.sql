ALTER TABLE tenants
    ADD COLUMN components jsonb NOT NULL DEFAULT '{}',
    ADD COLUMN has_logo boolean NOT NULL DEFAULT false;

CREATE TABLE workspace_logos (
    tenant_id uuid PRIMARY KEY REFERENCES tenants(id),
    content_type varchar(40) NOT NULL,
    content bytea NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE app_users
    ADD COLUMN phone varchar(40),
    ADD COLUMN locale varchar(10) NOT NULL DEFAULT 'fr',
    ADD COLUMN last_login_at timestamptz,
    ADD COLUMN default_car_id uuid,
    ADD COLUMN notification_preferences jsonb NOT NULL DEFAULT '{}',
    ADD COLUMN stripe_customer_id varchar(255),
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN inactivity_notified_at timestamptz;

ALTER TABLE stations
    ADD COLUMN site_area_id uuid,
    ADD COLUMN public_station boolean NOT NULL DEFAULT false,
    ADD COLUMN latitude numeric(22,6),
    ADD COLUMN longitude numeric(22,6),
    ADD COLUMN maximum_power_kw numeric(22,6),
    ADD COLUMN template_id uuid,
    ADD COLUMN exclude_from_smart_charging boolean NOT NULL DEFAULT false,
    ADD COLUMN deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN last_boot timestamptz,
    ADD COLUMN ocpp_version varchar(10) NOT NULL DEFAULT '1.6',
    ADD COLUMN firmware_status varchar(40),
    ADD COLUMN diagnostics_status varchar(40),
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX stations_site_area ON stations(tenant_id, site_area_id);

ALTER TABLE connectors
    ADD COLUMN type varchar(30),
    ADD COLUMN maximum_power_kw numeric(22,6),
    ADD COLUMN phases integer,
    ADD COLUMN current_power_w numeric(22,6),
    ADD COLUMN current_soc integer,
    ADD COLUMN meter_at timestamptz;

ALTER TABLE charging_tokens
    ADD COLUMN visual_id varchar(100),
    ADD COLUMN description varchar(200),
    ADD COLUMN default_token boolean NOT NULL DEFAULT false,
    ADD COLUMN last_used_at timestamptz;
CREATE INDEX charging_tokens_user ON charging_tokens(tenant_id, user_id);

ALTER TABLE charging_transactions
    ADD COLUMN site_area_id uuid,
    ADD COLUMN site_id uuid,
    ADD COLUMN last_energy_wh numeric(22,6),
    ADD COLUMN last_power_w numeric(22,6),
    ADD COLUMN last_meter_at timestamptz,
    ADD COLUMN soc_start integer,
    ADD COLUMN soc_last integer,
    ADD COLUMN inactivity_seconds bigint NOT NULL DEFAULT 0,
    ADD COLUMN charging_ended_at timestamptz,
    ADD COLUMN price numeric(22,6),
    ADD COLUMN currency varchar(3),
    ADD COLUMN pricing_definition_id uuid,
    ADD COLUMN billing_status varchar(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN invoice_id uuid,
    ADD COLUMN roaming_partner_id uuid,
    ADD COLUMN soft_stopped boolean NOT NULL DEFAULT false,
    ADD COLUMN start_notified boolean NOT NULL DEFAULT false,
    ADD COLUMN end_of_charge_notified boolean NOT NULL DEFAULT false;
CREATE INDEX transactions_user ON charging_transactions(tenant_id, user_id, started_at DESC);
CREATE INDEX transactions_station ON charging_transactions(tenant_id, station_id, started_at DESC);
CREATE INDEX transactions_active ON charging_transactions(tenant_id) WHERE stopped_at IS NULL;
CREATE INDEX meter_values_transaction ON meter_values(transaction_id, sampled_at);

ALTER TABLE station_commands
    ADD COLUMN callback jsonb;
CREATE INDEX station_commands_station ON station_commands(tenant_id, station_id, created_at DESC);

ALTER TABLE ocpi_partners
    ADD COLUMN last_ping_at timestamptz,
    ADD COLUMN last_push_at timestamptz,
    ADD COLUMN last_pull_at timestamptz,
    ADD COLUMN last_error text;

ALTER TABLE workspace_payment_settings
    ADD COLUMN user_billing_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN user_billing_mode varchar(20) NOT NULL DEFAULT 'PER_SESSION',
    ADD COLUMN tax_rate_id varchar(255);

CREATE TABLE site_users (
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    site_id uuid NOT NULL,
    user_id uuid NOT NULL,
    site_admin boolean NOT NULL DEFAULT false,
    site_owner boolean NOT NULL DEFAULT false,
    PRIMARY KEY (tenant_id, site_id, user_id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES app_users(tenant_id, id)
);
CREATE INDEX site_users_user ON site_users(tenant_id, user_id);

CREATE TABLE station_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL,
    station_id varchar(100) NOT NULL,
    type varchar(30) NOT NULL,
    connector_id integer,
    status varchar(60),
    error_code varchar(60),
    info text,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, station_id) REFERENCES stations(tenant_id, id)
);
CREATE INDEX station_events_station ON station_events(tenant_id, station_id, created_at DESC);
CREATE INDEX station_events_created ON station_events(created_at);

CREATE TABLE station_parameters (
    tenant_id uuid NOT NULL,
    station_id varchar(100) NOT NULL,
    parameter_key varchar(100) NOT NULL,
    parameter_value text,
    readonly boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, station_id, parameter_key),
    FOREIGN KEY (tenant_id, station_id) REFERENCES stations(tenant_id, id)
);

CREATE TABLE charging_profiles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    station_id varchar(100) NOT NULL,
    connector_id integer NOT NULL CHECK (connector_id >= 0),
    profile_id integer NOT NULL,
    purpose varchar(40) NOT NULL,
    stack_level integer NOT NULL,
    transaction_id integer,
    profile jsonb NOT NULL,
    source varchar(20) NOT NULL,
    command_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, station_id) REFERENCES stations(tenant_id, id)
);
CREATE INDEX charging_profiles_station ON charging_profiles(tenant_id, station_id, created_at DESC);

CREATE TABLE registration_tokens (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    token_hash char(64) NOT NULL,
    description varchar(200) NOT NULL,
    site_area_id uuid,
    expires_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, token_hash)
);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    type varchar(60) NOT NULL,
    user_id uuid,
    recipient text NOT NULL,
    subject text NOT NULL,
    channel varchar(20) NOT NULL,
    mail_id uuid,
    dedupe_key varchar(200),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_tenant ON notifications(tenant_id, created_at DESC);
CREATE INDEX notifications_dedupe ON notifications(tenant_id, dedupe_key, created_at);

CREATE TABLE asset_consumptions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    asset_id uuid NOT NULL,
    sampled_at timestamptz NOT NULL,
    power_w numeric(22,6) NOT NULL,
    energy_wh numeric(22,6),
    soc integer
);
CREATE INDEX asset_consumptions_asset ON asset_consumptions(tenant_id, asset_id, sampled_at);

CREATE TABLE asset_connections (
    asset_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    secrets_encrypted text,
    push_token_hash char(64) UNIQUE,
    last_success_at timestamptz,
    last_error text,
    last_attempt_at timestamptz
);

CREATE TABLE invoices (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    user_id uuid NOT NULL,
    stripe_account_id varchar(255) NOT NULL,
    stripe_invoice_id varchar(255) UNIQUE,
    number varchar(100),
    status varchar(20) NOT NULL,
    amount_minor bigint NOT NULL DEFAULT 0,
    currency varchar(3) NOT NULL,
    hosted_url text,
    pdf_url text,
    session_ids jsonb NOT NULL DEFAULT '[]',
    period_key varchar(20),
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, user_id) REFERENCES app_users(tenant_id, id)
);
CREATE INDEX invoices_tenant ON invoices(tenant_id, created_at DESC);
CREATE INDEX invoices_status ON invoices(status);

CREATE TABLE ocpi_jobs (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    partner_id uuid NOT NULL,
    module varchar(30) NOT NULL,
    direction varchar(10) NOT NULL,
    total integer NOT NULL DEFAULT 0,
    success integer NOT NULL DEFAULT 0,
    failure integer NOT NULL DEFAULT 0,
    errors jsonb NOT NULL DEFAULT '[]',
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    FOREIGN KEY (tenant_id, partner_id) REFERENCES ocpi_partners(tenant_id, id)
);
CREATE INDEX ocpi_jobs_partner ON ocpi_jobs(tenant_id, partner_id, started_at DESC);

CREATE TABLE ocpi_outbox (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    partner_id uuid NOT NULL,
    module varchar(30) NOT NULL,
    method varchar(10) NOT NULL,
    url text,
    path text NOT NULL,
    body jsonb NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    last_error text,
    FOREIGN KEY (tenant_id, partner_id) REFERENCES ocpi_partners(tenant_id, id)
);
CREATE INDEX ocpi_outbox_pending ON ocpi_outbox(next_attempt_at) WHERE sent_at IS NULL;

CREATE TABLE smart_charging_plans (
    site_area_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    plan jsonb NOT NULL,
    computed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE integration_secrets (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    secrets_encrypted text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
