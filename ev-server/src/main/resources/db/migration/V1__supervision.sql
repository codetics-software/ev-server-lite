CREATE TABLE tenants (
    id uuid PRIMARY KEY,
    slug varchar(63) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE app_users (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    email varchar(320) NOT NULL,
    display_name varchar(200) NOT NULL,
    password_hash text,
    role varchar(20) NOT NULL CHECK (role IN ('ADMIN','OPERATOR','VIEWER','DRIVER')),
    enabled boolean NOT NULL DEFAULT true,
    email_verified boolean NOT NULL DEFAULT false,
    eula_version varchar(30),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email), UNIQUE (tenant_id, id)
);
CREATE TABLE oauth_identities (
    issuer text NOT NULL, subject text NOT NULL,
    user_id uuid NOT NULL REFERENCES app_users(id),
    PRIMARY KEY (issuer, subject)
);
CREATE TABLE access_tokens (
    token_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX access_tokens_user ON access_tokens(user_id);
CREATE TABLE auth_challenges (
    token_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id),
    kind varchar(20) NOT NULL CHECK (kind IN ('RESET','VERIFY','OAUTH_EXCHANGE')),
    expires_at timestamptz NOT NULL
);
CREATE TABLE auth_attempts (
    subject_hash char(64) PRIMARY KEY,
    window_start timestamptz NOT NULL,
    attempts integer NOT NULL
);
CREATE TABLE mail_outbox (
    id uuid PRIMARY KEY,
    recipient text NOT NULL, subject text NOT NULL, body text NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    sent_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE records (
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    kind varchar(60) NOT NULL, id uuid NOT NULL,
    name varchar(200) NOT NULL,
    data jsonb NOT NULL DEFAULT '{}',
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, kind, id)
);
CREATE INDEX records_search ON records(tenant_id, kind, updated_at DESC);
CREATE TABLE stations (
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    id varchar(100) NOT NULL, name varchar(200) NOT NULL,
    secret_hash text NOT NULL,
    vendor varchar(100), model varchar(100), firmware_version varchar(100),
    enabled boolean NOT NULL DEFAULT true,
    last_seen timestamptz, connected boolean NOT NULL DEFAULT false,
    metadata jsonb NOT NULL DEFAULT '{}',
    PRIMARY KEY (tenant_id,id)
);
CREATE TABLE connectors (
    tenant_id uuid NOT NULL, station_id varchar(100) NOT NULL,
    connector_id integer NOT NULL CHECK (connector_id >= 0),
    status varchar(30) NOT NULL DEFAULT 'Unavailable', error_code varchar(60) NOT NULL DEFAULT 'NoError',
    status_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id,station_id,connector_id),
    FOREIGN KEY (tenant_id,station_id) REFERENCES stations(tenant_id,id)
);
CREATE TABLE charging_tokens (
    tenant_id uuid NOT NULL REFERENCES tenants(id), uid varchar(36) NOT NULL,
    user_id uuid,
    type varchar(20) NOT NULL DEFAULT 'RFID',
    enabled boolean NOT NULL DEFAULT true,
    expires_at timestamptz,
    PRIMARY KEY (tenant_id,uid),
    FOREIGN KEY (tenant_id,user_id) REFERENCES app_users(tenant_id,id)
);
CREATE TABLE charging_transactions (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL, station_id varchar(100) NOT NULL,
    connector_id integer NOT NULL CHECK (connector_id > 0),
    token_uid varchar(36) NOT NULL,
    user_id uuid, vehicle_id uuid,
    started_at timestamptz NOT NULL, stopped_at timestamptz,
    meter_start_wh numeric(22,6) NOT NULL CHECK (meter_start_wh >= 0),
    meter_stop_wh numeric(22,6),
    stop_reason varchar(60),
    authorization_status varchar(30) NOT NULL,
    tariff_snapshot jsonb NOT NULL DEFAULT '{}',
    FOREIGN KEY (tenant_id,station_id,connector_id) REFERENCES connectors(tenant_id,station_id,connector_id),
    FOREIGN KEY (tenant_id,user_id) REFERENCES app_users(tenant_id,id),
    CHECK (stopped_at IS NULL OR stopped_at >= started_at)
);
CREATE UNIQUE INDEX one_active_transaction_per_connector ON charging_transactions(tenant_id,station_id,connector_id) WHERE stopped_at IS NULL;
CREATE INDEX transactions_tenant ON charging_transactions(tenant_id,started_at DESC);
CREATE TABLE meter_values (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL, station_id varchar(100) NOT NULL,
    connector_id integer NOT NULL, transaction_id integer REFERENCES charging_transactions(id),
    sampled_at timestamptz NOT NULL, samples jsonb NOT NULL,
    FOREIGN KEY (tenant_id,station_id) REFERENCES stations(tenant_id,id)
);
CREATE TABLE station_commands (
    id uuid PRIMARY KEY, tenant_id uuid NOT NULL, station_id varchar(100) NOT NULL,
    action varchar(80) NOT NULL, request jsonb NOT NULL,
    state varchar(20) NOT NULL CHECK (state IN ('QUEUED','SENT','SUCCEEDED','FAILED','TIMED_OUT')),
    result jsonb, created_at timestamptz NOT NULL DEFAULT now(),
    deadline timestamptz NOT NULL, completed_at timestamptz,
    FOREIGN KEY (tenant_id,station_id) REFERENCES stations(tenant_id,id)
);
CREATE TABLE ocpp_calls (
    tenant_id uuid NOT NULL, station_id varchar(100) NOT NULL,
    message_id varchar(36) NOT NULL, action varchar(80) NOT NULL,
    request_hash char(64) NOT NULL, response jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id,station_id,message_id),
    FOREIGN KEY (tenant_id,station_id) REFERENCES stations(tenant_id,id)
);
CREATE TABLE audit_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id), actor text NOT NULL,
    action varchar(100) NOT NULL, target text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ocpi_partners (
    id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id),
    name varchar(200) NOT NULL, country_code char(2) NOT NULL, party_id varchar(3) NOT NULL,
    local_role varchar(4) NOT NULL CHECK (local_role IN ('cpo','emsp')),
    token_hash char(64) NOT NULL UNIQUE,
    remote_token_encrypted text,
    remote_url text,
    registered boolean NOT NULL DEFAULT false, enabled boolean NOT NULL DEFAULT true,
    UNIQUE (tenant_id,id)
);
CREATE TABLE ocpi_objects (
    tenant_id uuid NOT NULL, partner_id uuid NOT NULL,
    module varchar(30) NOT NULL, object_path text NOT NULL,
    direction varchar(10) NOT NULL CHECK (direction IN ('SENDER','RECEIVER')),
    data jsonb NOT NULL, last_updated timestamptz NOT NULL,
    PRIMARY KEY (tenant_id,partner_id,module,direction,object_path),
    FOREIGN KEY (tenant_id,partner_id) REFERENCES ocpi_partners(tenant_id,id)
);
CREATE TABLE migration_documents (
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    collection_name text NOT NULL, source_id text NOT NULL,
    source_sha256 char(64) NOT NULL, document jsonb NOT NULL,
    raw_document text NOT NULL,
    imported_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id,collection_name,source_id)
);
