# Connecting charging stations over `wss://`

This guide explains how to publish the OCPP endpoint over TLS (`wss://`) so that real charging stations can connect, how to configure each security profile, and how to diagnose a station that cannot connect. [Guide de déploiement (français)](deploiement/README.md).

## How a `wss://` connection reaches the server

```text
Station ──TLS (wss://APP_HOST/ocpp/{workspace}/{station})──► Caddy / nginx / load balancer
                                                              │ terminates TLS
                                                              │ adds X-Forwarded-Proto: https
                                                              ▼
                                                   ev-server :8080 (plain HTTP, private network)
```

Two independent things have to work:

1. **The TLS handshake**, between the station and the proxy. If it fails, the server never sees the station: nothing appears in the station's events, and the station only reports a generic "connection failed".
2. **The WebSocket handshake**, checked by the server: workspace, station identifier, password or certificate, security profile, and OCPP version.

## Why stations failed to connect over `wss://`

Tests against the shipped `deploy/` stack, using a simulated station, showed connections failing during the TLS handshake **before reaching the server**, even with a correct password:

| Station behaviour | Before | Cause | Fix |
|---|---|---|---|
| Sends no SNI (configured with an IP address, or an old TLS library) | `tlsv1 alert internal error` | Caddy had no certificate to offer without SNI | `default_sni {$APP_HOST}` in the Caddyfile, `default_server` in nginx |
| TLS 1.2 stack that only supports RSA certificates | `handshake failure` | Caddy issues ECDSA certificates by default | `key_type rsa2048`. The RSA Let's Encrypt chain also ends at ISRG Root X1, which far more station trust stores contain |
| Station sends a leftover `Authorization` header with no password (`Basic ` empty, a vendor default user such as `admin:`) while set to profile 0 | HTTP 401 | The server refused any unreadable header, even when no authentication was required | Ignored for profile 0 stations only. It is still refused for profiles 1 to 3 |
| A single malformed OCPP 1.6 frame | Connection closed (1007) | The server closed the socket, so the station reconnected in a loop | The frame is answered with a CallError when it has an identifier, otherwise ignored. The connection stays open |

The connection outcomes now go to the server log at INFO level (one line per station and reason per minute, never any secret), as well as to the station's events and the `ocpp.handshakes` metric.

## Configuration with the provided `deploy/` stack (recommended)

1. Point `APP_HOST` (DNS A/AAAA record) to the machine and open ports 80 and 443.
2. In `deploy/.env`: `APP_HOST`, `ACME_EMAIL`.
3. In `deploy/server.env`:

   ```text
   # Address shown in the commissioning sheet and on registration tokens.
   OCPP_PUBLIC_URL=wss://supervision.example.com/ocpp
   # Only if some stations must connect without any password (profile 0, see below).
   OCPP_ALLOW_UNAUTHENTICATED=false
   ```

   `docker-compose.yml` already sets `OCPP_TRUSTED_PROXIES=172.30.0.10/32` (Caddy's fixed address). That setting is what makes the server trust `X-Forwarded-Proto: https`, and so count a connection as TLS.
4. `docker compose up -d --build`.
5. On the station:

   | Station setting | Value |
   |---|---|
   | Central system / CSMS URL | `wss://APP_HOST/ocpp/{workspace}` (most stations append their identifier). Otherwise use `wss://APP_HOST/ocpp/{workspace}/{station}` |
   | Charge point identity | The station identifier, exactly as created in the dashboard (case-sensitive) |
   | Basic auth user / password | The identifier, and the password shown once when the station was created (40 hex characters) |
   | Port | 443 |
   | OCPP version / subprotocol | `ocpp1.6` or `ocpp2.0.1` (the station version setting can stay on Auto) |
   | `SecurityProfile` (1.6) / `NetworkConnectionProfile.securityProfile` (2.0.1) | 2 for `wss://` with a password |

6. In the dashboard, set the station's security profile to 2 once it has connected over `wss://`. A profile-2 station is refused over `ws://`.

### Upgrading an existing installation

Caddy keeps serving the certificate already stored in its `caddy-data` volume until renewal, which may still be ECDSA. Force it to issue the RSA certificate once:

```sh
cd deploy
docker compose exec caddy sh -c 'rm -rf /data/caddy/certificates/*/"$APP_HOST"'
docker compose restart caddy
# Check: the public key must be 2048 bit (RSA)
echo | openssl s_client -connect APP_HOST:443 -servername APP_HOST 2>/dev/null | grep "Server public key"
```

## Security profiles over `wss://`

| Profile | Station sends | Server requires | Notes |
|---|---|---|---|
| 0 | Nothing (or an unreadable leftover header) | `OCPP_ALLOW_UNAUTHENTICATED=true` **and** the station set to profile 0 in the dashboard | TLS still encrypts the link, but anyone who knows the identifier can impersonate the station. Use only for stations that cannot store a password |
| 1 | `Authorization: Basic base64(id:password)` | Password | Works over `ws://` and `wss://` |
| 2 | Same as 1, over `wss://` | Password and TLS, as seen directly or through a proxy listed in `OCPP_TRUSTED_PROXIES` | Recommended default |
| 3 | Client certificate (mTLS) | Certificate chain to `OCPP_CLIENT_CA` (or the signing CA), CN = identifier or pinned fingerprint | Use the separate `OCPP_HOST` (`deploy/caddy/sites/ocpp-mtls.caddy.example`) |

A station can always connect with a stronger profile than its own, never a weaker one.

**A station that must use `wss://` without a password** therefore needs:

```text
# deploy/server.env
OCPP_ALLOW_UNAUTHENTICATED=true
```

and its security profile set to **0** in the dashboard (Station › Security). Unknown stations are never accepted without a password. Auto-registration (registration tokens) always uses the token as a password.

## TLS requirements of charging stations

The OCPP security specifications (OCPP 1.6 security whitepaper, OCPP 2.0.1 part 2) require TLS 1.2 or later. They allow stations to support only a small set of cipher suites, including `TLS_RSA_WITH_AES_128_GCM_SHA256` and `TLS_RSA_WITH_AES_256_GCM_SHA384` for RSA-based stations.

| Proxy | RSA certificate | TLS 1.2 ECDHE-RSA | Static RSA (`TLS_RSA_WITH_AES_*_GCM`) | No SNI |
|---|---|---|---|---|
| Caddy (`deploy/caddy`) | yes (`key_type rsa2048`) | yes | **no**: not implemented by Go/Caddy | yes (`default_sni`) |
| nginx (`deploy/nginx`) | `certbot --key-type rsa` | yes | yes (`AES128-GCM-SHA256`, last in the list) | yes (`default_server`) |
| Spring Boot (`SERVER_SSL_*`) | your certificate | yes | JDK-dependent | yes |

If a station only supports static-RSA suites (the server log shows nothing, and the station logs a handshake failure), put nginx in front of the server with `deploy/nginx/ev-supervision.conf`.

### Certificate trust on the station

The station must trust the CA of the server certificate. Many stations ship an old trust store:

- with Let's Encrypt, the RSA chain (ISRG Root X1) is the most widely accepted;
- if the station only accepts a specific CA, install that CA's certificate on the proxy (`tls cert.pem key.pem` in Caddy, `ssl_certificate` in nginx);
- some stations need the root CA uploaded through their web interface, or by `InstallCertificate` (`CentralSystemRootCertificate` / `CSMSRootCertificate`) from the dashboard while connected over `ws://`;
- the station clock must be correct (NTP), otherwise every certificate looks expired or not yet valid.

## Diagnosing a station that cannot connect

Run these from a machine on the station's network.

```sh
# 1. TLS: must print a 2048-bit key and a TLS 1.2 or 1.3 cipher.
echo | openssl s_client -connect APP_HOST:443 -servername APP_HOST -tls1_2 2>/dev/null \
  | grep -E "Server public key|Cipher is|Verify return code"
# Without SNI, like a station configured by IP:
echo | openssl s_client -connect APP_HOST:443 -noservername 2>/dev/null | grep -E "subject=|Cipher is"

# 2. WebSocket handshake with the station's credentials: 101 = accepted, 401 = refused.
curl --http1.1 -sS -o /dev/null -w '%{http_code}\n' -u 'STATION-ID:PASSWORD' \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
  -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Sec-WebSocket-Protocol: ocpp1.6' \
  --max-time 5 https://APP_HOST/ocpp/WORKSPACE/STATION-ID

# 3. Reason of a refusal (server log)
docker compose logs server | grep "Connexion OCPP refusée"
```

| Log reason (`Connexion OCPP refusée … : …`) | Meaning | Action |
|---|---|---|
| `TLS requis` | Profile ≥ 2 but the server did not see TLS | Connect with `wss://`. Behind your own proxy, add its address to `OCPP_TRUSTED_PROXIES` and make it send `X-Forwarded-Proto: https` |
| `mot de passe absent` | No password, profile ≥ 1 | Set the password on the station, or use profile 0 (see above) |
| `mot de passe invalide` | Wrong password | Reset it in the dashboard (Station › Security), configure it on the station |
| `en-tête d’authentification invalide` | Basic user ≠ station identifier, or the header is not Base64 | The Basic auth user must be exactly the station identifier |
| `profil 0 désactivé sur le serveur` | Profile 0 station, `OCPP_ALLOW_UNAUTHENTICATED=false` | Enable it, or give the station a password |
| `borne inconnue` / `espace client inconnu` | Identifier or workspace not found (case-sensitive) | Check the URL: `/ocpp/{workspace}/{station}` |
| `aucune version OCPP prise en charge proposée` | Subprotocol not `ocpp1.6` / `ocpp2.0.1` | Set the OCPP version on the station |
| `trop de tentatives de connexion` (HTTP 429) | Over 60 attempts per station and address in 15 min | Wait. Check that the station is not stuck in a reconnect loop |
| *nothing in the log* | TLS failed before the server | Steps 1 and the TLS section above. Check `docker compose logs caddy` |

The `ocpp.handshakes` metric (`outcome`, `reason`, `tls` tags), the HTTP span of the upgrade request (`GET /ocpp/...`, status 101, 401 or 429) and the `ocpp.call` spans show the same information in your monitoring tool: see [observability.md](observability.md).
