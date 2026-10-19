# EV Supervision Lite architecture

Built by Kevin Vavelin, Codetics. Product home: https://codetics.fr.

The Kotlin Spring Boot server owns PostgreSQL persistence, authorization, charging
state, station communication and roaming. The Next.js dashboard uses the same API
through a server-side proxy; credentials remain in HttpOnly cookies.

## Ownership and identity

Every operational object belongs to one tenant. Tenant identity comes from an
authenticated principal, never an editable request field. A company contains
sites; a site contains site areas and stations. Station connector numbers are
scoped to a station. An OCPP connector is not automatically an OCPI EVSE: explicit
location/EVSE/connector mappings are required before publication.

Tokens belong to a tenant and optionally to a user. A charging transaction belongs
to a station and connector, and may reference a user, token and vehicle. A vehicle
is always optional. Meter readings retain their original timestamp, unit, context,
phase and signed values. Energy and financial amounts use decimal arithmetic.

Station connectivity, connector availability, authorization and transaction state
are independent facts. Outbound commands have a correlation ID, deadline and
persisted outcome. An Accepted command response does not prove that charging began.

OCPI objects are scoped by partner, role, module and object path. Received objects
are partner-owned copies; local objects are separately managed. CDRs are immutable.
Historical transactions and imported documents must not be deleted through the
generic configuration API.

## Data migration

Mongo Extended JSON documents are archived in PostgreSQL with a collection,
source ID, raw UTF-8 document text and SHA-256 fingerprint. A JSONB copy supports
querying while the raw text preserves key ordering and number formatting that
JSONB can normalize. Import is atomic and idempotent.
Archival preserves fields; it does not establish application parity or populate
the operational relational tables automatically. A verified collection-specific
mapping and reconciliation is required before a production cutover.

## Acceptance

[parity-inventory.json](parity-inventory.json) is the source-derived checklist.
The findings and review scope are in [migration-audit.md](migration-audit.md).
Pending means unverified;
no component is considered equivalent merely because a route or table exists.
Protocol claims are limited to tested versions and implemented actions. The full
rewrite is accepted only after workflow, authorization, protocol, financial,
integration, data-reconciliation and browser evidence closes this checklist.
