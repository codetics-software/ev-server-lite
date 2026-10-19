# Authentication configuration

Use one public dashboard origin for browser traffic. Next.js forwards only the
required cookie, content type and CSRF headers to the private Spring server.
OAuth initiation and callback routes are proxied at the same browser origin.
Provider tokens and secrets are never placed in browser storage.

## Providers

| Provider | Required environment variables | Callback |
| --- | --- | --- |
| Google | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `/login/oauth2/code/google` |
| Microsoft | `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, `MICROSOFT_TENANT_ID` | `/login/oauth2/code/microsoft` |
| Apple | `APPLE_CLIENT_ID`, `APPLE_CLIENT_SECRET` | `/login/oauth2/code/apple` |

Register the complete callback URL under `APP_PUBLIC_URL` in each provider's
developer console. Unconfigured providers do not appear on the sign-in page.
Spring Security validates OAuth state and OIDC ID-token signatures, audience,
issuer, timestamps and nonce. Google and Microsoft authorization requests use
PKCE. Apple uses its confidential-client exchange and `form_post` response mode.

Microsoft configuration uses a specific Entra directory UUID. The `common`
authority is deliberately not accepted because it requires additional issuer
and tenant-allowlist validation. Microsoft identities without a verified email
claim must be linked from an existing authenticated account; email is not used
as an identity proof.

Apple's client secret is a signed JWT, not a normal static password. Generate it
using your Apple team ID, key ID, Services ID and ES256 private key according to
Apple's instructions. Supply the resulting JWT as `APPLE_CLIENT_SECRET`, keep the
private key outside this repository, and rotate the JWT before its expiration.
Apple name information may only be supplied on the first authorization; an email
or existing profile name remains usable when a name is absent.

Apple requires a registered HTTPS return URL. Keep `COOKIE_SECURE=true` and
`OAUTH_COOKIE_SAME_SITE=none` for the temporary OAuth session cookie so it arrives
with Apple's cross-site POST callback. The local HTTP/H2 profile is for password
and repository tests; it does not establish a working live Apple callback.

## Account linking and authorization

Identity keys are `(issuer, subject)`. A matching email never automatically links
a provider to an existing account. Sign in first, then use **My account → Connect**.
The CSRF-protected linking operation binds the authenticated account and original
session-token hash to the temporary OAuth session. The callback checks that the
original application session remains valid before linking the identity.

New public OAuth registrations require self registration, a fixed public tenant,
and a verified email claim. They always receive Driver permissions. The OAuth
provider cannot choose a tenant or elevate a role. Disabled accounts and tenants
are checked on every application credential lookup.

Sessions are opaque random values, with only SHA-256 hashes stored in PostgreSQL.
Logout revokes the current value; all-device logout and password reset revoke all
values. Administrator role/account changes also revoke existing sessions.

Browser mutations require the Spring CSRF token. Obtain it from
`GET /api/v1/auth/csrf` and send the returned header with the next mutation while
preserving the CSRF cookie. Apple's callback is the narrowly scoped exception,
protected by OAuth state, nonce and Spring's temporary authorization session.

## Official references

- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
- [Microsoft OpenID Connect](https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc)
- [Apple token validation](https://developer.apple.com/documentation/signinwithapplerestapi/generate-and-validate-tokens)
