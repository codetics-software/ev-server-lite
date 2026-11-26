# Observability with OpenTelemetry

The server exports **traces, metrics and logs** over OTLP, and the Caddy edge exports a trace for every incoming request. Together they cover every HTTP request (dashboard, API, OCPI, Stripe webhooks) and all OCPP WebSocket traffic. Nothing is exported until you enable it. [Guide de déploiement (français)](deploiement/README.md).

## What is collected

| Source | Signal | Content |
|---|---|---|
| Caddy (`deploy/caddy/observability/tracing.caddy`) | traces | One root span per incoming request (dashboard pages, API, `/ocpp`, `/ocpi`). The W3C `traceparent` header is passed to the server and the dashboard |
| Dashboard proxy | context propagation | Forwards a valid `traceparent`/`tracestate` to the server, so browser → Caddy → API calls form a single trace |
| Server: HTTP | traces + `http.server.requests` | Every request, including the WebSocket upgrade of each station (`GET /ocpp/...` → 101, 401 or 429). Health probes are excluded |
| Server: OCPP | traces | One span per message from a station (observation `ocpp.call`, span name `OCPP <action>`, for example `OCPP BootNotification`), with `ocpp.action`, `ocpp.version`, `ocpp.outcome` (`ok` or CallError code), `ocpp.station`, `ocpp.tenant` and `ocpp.message.id` |
| Server: OCPP | metrics | See the table below |
| Server: JVM, Tomcat, Hikari, outgoing HTTP | metrics, traces | Spring Boot and Micrometer defaults |
| Server: logs | logs | Every log line, with `trace_id`/`span_id` for correlation. Console lines also show `[traceId-spanId]` |

### OCPP metrics

| Metric | Type | Tags |
|---|---|---|
| `ocpp.connections` | gauge | `ocpp.version` |
| `ocpp.handshakes` | counter | `outcome` (`accepted`, `rejected`), `reason` (`tls_required`, `invalid_password`, `password_missing`, `invalid_authorization`, `unknown_station`, `unknown_tenant`, `station_disabled`, `certificate_missing`, `certificate_refused`, `profile0_disabled`, `unsupported_protocol`, `rate_limited`, `bad_path`, `bad_station_id`, `bad_request`), `tls`, `security.profile` |
| `ocpp.messages` | counter | `direction` (`inbound`, `outbound`), `kind` (`call`, `result`, `error`, `invalid`), `ocpp.action`, `ocpp.version` |
| `ocpp.message.size` | distribution (bytes) | `direction`, `ocpp.version` |
| `ocpp.call` | timer (from the span) | `ocpp.action`, `ocpp.version`, `ocpp.outcome`, `error` |
| `ocpp.commands` | counter | `ocpp.action`, `ocpp.version`, `state` (`sent`, `accepted`, `error`, `failed`) |
| `ocpp.disconnections` | counter | `ocpp.version`, `close.code` |

Metric tags never contain station-supplied free text. An unknown action is recorded as `other`, and station identifiers only appear on spans. Telemetry failures are caught and never close a station connection.

## Enabling it with Docker Compose (all-in-one backend)

The `observability` profile starts `grafana/otel-lgtm`: an OpenTelemetry collector with Tempo (traces), Loki (logs), Prometheus (metrics) and Grafana.

```sh
cd deploy
# .env
COMPOSE_PROFILES=local-db,observability
OTEL_ENABLED=true
GRAFANA_ADMIN_PASSWORD=choose-a-long-password

# Caddy edge traces
cp caddy/observability/tracing.caddy.example caddy/observability/tracing.caddy

docker compose up -d
```

Grafana only listens on the host (`127.0.0.1:3001`). From your workstation:

```sh
ssh -L 3001:127.0.0.1:3001 user@server   # then open http://localhost:3001
```

- **Explore › Tempo** (TraceQL): `{name=~"OCPP .*"}` for every OCPP message, `{span.ocpp.station="CS-01"}` for one station, `{span.ocpp.outcome!="ok" && name=~"OCPP .*"}` for refused messages. `{resource.service.name="caddy"} && {resource.service.name="ev-server"}` returns requests traced from the edge to the server.
- **Explore › Prometheus**: for example `sum by (reason) (rate(ocpp_handshakes_total{outcome="rejected"}[5m]))`, or `ocpp_connections`.
- **Explore › Loki**: `{service_name="ev-server"} |= "Connexion OCPP refusée"`. Each line carries its `trace_id`, which you can open in Tempo.

## Sending to another backend

Any OTLP-compatible backend works (an OpenTelemetry Collector, Grafana Cloud, Datadog Agent, Elastic, Honeycomb, New Relic, Dynatrace and others). Both the server and Caddy use **OTLP over HTTP** (port 4318 by convention). The server appends `/v1/traces`, `/v1/metrics` and `/v1/logs` to the endpoint.

| Variable | Service | Default | Purpose |
|---|---|---|---|
| `OTEL_ENABLED` | server | `false` | Turns on trace, metric and log export |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | server | `http://otel-lgtm:4318` (compose), `http://localhost:4318` | OTLP/HTTP base URL, without `/v1/...` |
| `OTEL_SERVICE_NAME` | server | `ev-server` | `service.name` resource attribute |
| `OTEL_ENVIRONMENT` | server | `production` | `deployment.environment.name` |
| `OTEL_TRACES_SAMPLING_PROBABILITY` | server | `1.0` | Share of traces kept (1.0 = every request; lower it under heavy load) |
| `OTEL_METRICS_STEP` | server | `30s` | Metric export interval |
| `OTEL_RESOURCE_ATTRIBUTES` | server | | Extra attributes, e.g. `service.instance.id=vm-1` |
| `CADDY_OTEL_EXPORTER_OTLP_ENDPOINT` | caddy (compose) | `http://otel-lgtm:4318` | OTLP/HTTP base URL for Caddy |

For backends that need an API key, run an OpenTelemetry Collector next to the server (or as a Kubernetes DaemonSet), point `OTEL_EXPORTER_OTLP_ENDPOINT` at it, and keep the key in the collector configuration rather than in the server environment.

## Kubernetes

Add the variables to `ev-server-env` (for example `OTEL_ENABLED=true`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.observability:4318`). Allow egress from the `ev-server` pods to the collector in `network-policy.yaml`.

## Overhead

With `OTEL_ENABLED=false` (the default), no exporter runs and nothing leaves the server. When enabled, spans and logs are batched (queues of 2048, 512 per batch) and dropped rather than blocking if the collector is unreachable. A failing collector never affects charging stations or the API.
