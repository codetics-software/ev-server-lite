# EV Supervision Lite

EV supervision built by **Kevin Vavelin, Codetics**.

A Kotlin Spring Boot server with PostgreSQL persistence and a Next.js dashboard
built with shadcn/ui. Vehicles are optional throughout the charging workflow.

For better EV supervision, contact **[Codetics](https://codetics.fr)**.

## Projects

| Project | Stack | Purpose |
| --- | --- | --- |
| `ev-server` | Kotlin, Spring Boot 4.1, Spring Security, Spring Data JPA | Accounts, station communication, operations API and roaming |
| `ev-dashboard` | Next.js 16, React, TypeScript, shadcn/ui | Supervision workspace and operator controls |

Production uses PostgreSQL and Flyway migrations. Hibernate validates the schema;
it does not modify production tables. Application queries use JPA repositories,
Criteria specifications and parameterized JPQL. No runtime native SQL is used.

## Local development

Requirements: Java 25 or later, Node.js 22 or later, and the included Maven wrapper.

For an isolated H2 test workspace, run the server with the `local` profile:

```sh
cd ev-server
SPRING_PROFILES_ACTIVE=local \
BOOTSTRAP_EMAIL=admin@example.test \
BOOTSTRAP_PASSWORD='replace-with-a-long-local-test-password' \
./mvnw spring-boot:run
```

In a second terminal:

```sh
cd ev-dashboard
npm ci
npm run dev
```

Open `http://localhost:3000/auth/login`, enter workspace `codetics` and the
bootstrap account credentials. Bootstrap only creates an administrator when the
database contains no users. Remove bootstrap credentials after initial setup.
The H2 profile uses an in-memory database and loses its data when stopped.

## PostgreSQL

Create a dedicated database and role. Configure these environment variables:

```dotenv
DATABASE_URL=jdbc:postgresql://localhost:5432/ev_supervision
DATABASE_USER=ev_supervision
DATABASE_PASSWORD=replace-with-a-secret
APP_PUBLIC_URL=https://supervision.example.com
COOKIE_SECURE=true
```

Start without the `local` profile. Use HTTPS at the public reverse proxy. The
dashboard's `EV_SERVER_URL` points to the private Spring server and must remain
server-side. Set dashboard `APP_PUBLIC_URL` to the same public dashboard origin.

## Authentication

Spring Security provides CSRF protection, role checks, OAuth/OIDC validation and
the security filter chains. Application sessions use random opaque credentials,
stored as hashes in the database and sent in HttpOnly cookies. New passwords use
Spring Security PBKDF2; legacy BCrypt verification remains available. Reset and
verification links are single-use and expire after 30 minutes.

Roles are Administrator, Operator, Viewer and Driver. Every operational lookup
is scoped to the authenticated tenant. Driver session queries also require
ownership. CPO/eMSP are OCPI market roles, distinct from user permissions.

Google, Microsoft and Apple sign-in are optional. See
[docs/authentication.md](docs/authentication.md) for configuration, account linking,
Apple's callback requirements and credential rotation. Provider credentials must
be supplied through environment variables; none are bundled.

Self registration is disabled by default. Enable `SIGNUP_ENABLED=true` and set
`PUBLIC_TENANT` only when intentionally accepting new Driver accounts. Configure
SMTP and `MAIL_ENABLED=true` for verification and reset delivery.

## Station communication

Provision a station from the dashboard. Store the one-time station password.
Connect using OCPP 1.6 JSON over WebSocket with subprotocol `ocpp1.6`:

```text
wss://your-server/ocpp/{workspace}/{stationIdentity}
Authorization: Basic <base64(stationIdentity:stationPassword)>
```

The implemented transaction flow handles authorization, start, timestamped meter
values, status, heartbeat and stop messages. Responses are cached by station and
message ID for idempotent replay. Outbound commands are persisted, correlated,
deadline-bound and exposed in Command activity. Their responses do not by
themselves prove that a charging session began.

Only verified protocol coverage may be treated as supported. SOAP, complete
schema validation and complete roaming workflows remain acceptance items; see
the delivery status before connecting real stations or partners.

## Roaming

OCPI 2.1.1 discovery, credential exchange and object storage interfaces are
implemented. Configure an OCPI partner and a Base64-encoded 32-byte
`APP_ENCRYPTION_KEY` before provisioning credentials. Partner secrets are encrypted
with AES-GCM. The remote origin is pinned to the administrator-configured URL.
Keep the encryption key outside the database and back it up separately.

## Verification and documentation

```sh
cd ev-server
./mvnw test
```

```sh
cd ev-dashboard
npm run lint
npm run build
```

The administrator-only OpenAPI document is at `/api/v1/openapi` on the Spring
server. Architecture and data ownership are documented in
[docs/architecture.md](docs/architecture.md). Local H2 verification does not prove
PostgreSQL migration compatibility or interoperability certification.

Dashboard PNG captures belong in `screenshots/`.

## License and attribution

Original Codetics code uses the [Codetics Attribution License 1.0](LICENSE), which
requires visible author credit. Retain `NOTICE` and applicable third-party
licenses when distributing. The dashboard includes the required author credit.
