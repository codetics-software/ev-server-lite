import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { proxy } from "@/lib/proxy";

const upstream = (init: ResponseInit = {}, body: BodyInit | null = "ok") =>
  new Response(body, {
    status: 200,
    ...init,
    headers: init.headers ?? [
      ["content-type", "application/json"],
      ["location", "/next"],
      ["set-cookie", "SESSION=a; Path=/; HttpOnly"],
      ["set-cookie", "XSRF-TOKEN=b; Path=/"],
      ["x-internal", "secret"],
    ],
  });

type ProxyInit = NonNullable<ConstructorParameters<typeof NextRequest>[1]>;

const request = (path: string, init: ProxyInit = {}) =>
  new NextRequest(`https://app.example/api/backend/${path}`, init);

describe("dashboard proxy", () => {
  beforeEach(() => {
    vi.stubEnv("EV_SERVER_URL", "http://server.internal:8080");
    vi.stubEnv("APP_PUBLIC_URL", "https://app.example");
    vi.stubEnv("EV_LOCAL_TEST_CLASSPATH", "");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("only proxies the server API and sign-in paths", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    expect((await proxy(request("actuator/env"), "actuator/env")).status).toBe(404);
    expect((await proxy(request("x"), "api/v1/../actuator")).status).toBe(404);
    expect((await proxy(request("x"), "api/v1/./stations")).status).toBe(404);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("forwards allowed headers and rebuilds the client address chain", async () => {
    const fetch = vi.fn().mockResolvedValue(upstream());
    vi.stubGlobal("fetch", fetch);
    const response = await proxy(
      request("api/v1/stations?page=2", {
        headers: {
          cookie: "SESSION=a",
          "accept-language": "en",
          authorization: "Bearer stolen",
          host: "evil.example",
          "x-forwarded-for": "1.2.3.4, not-an-ip, 5.6.7.8",
          "x-real-ip": "9.9.9.9",
        },
      }),
      "api/v1/stations",
    );

    const [target, init] = fetch.mock.calls[0];
    expect(String(target)).toBe("http://server.internal:8080/api/v1/stations?page=2");
    expect(init.method).toBe("GET");
    expect(init.redirect).toBe("manual");
    expect(init.body).toBeUndefined();
    const sent = new Headers(init.headers);
    expect(sent.get("cookie")).toBe("SESSION=a");
    expect(sent.get("accept-language")).toBe("en");
    expect(sent.get("authorization")).toBeNull();
    expect(sent.get("host")).toBeNull();
    expect(sent.get("x-forwarded-for")).toBe("1.2.3.4, 5.6.7.8, 9.9.9.9");

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("location")).toBe("/next");
    expect(response.headers.get("x-internal")).toBeNull();
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.getSetCookie()).toEqual([
      "SESSION=a; Path=/; HttpOnly",
      "XSRF-TOKEN=b; Path=/",
    ]);
  });

  it("forwards a valid W3C trace context and drops malformed ones", async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(upstream()));
    vi.stubGlobal("fetch", fetch);
    const parent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    await proxy(
      request("api/v1/stations", { headers: { traceparent: parent, tracestate: "caddy=1" } }),
      "api/v1/stations",
    );
    let sent = new Headers(fetch.mock.calls[0][1].headers);
    expect(sent.get("traceparent")).toBe(parent);
    expect(sent.get("tracestate")).toBe("caddy=1");

    await proxy(
      request("api/v1/stations", {
        headers: { traceparent: "00-not-a-trace-id-01", tracestate: "a=1" },
      }),
      "api/v1/stations",
    );
    sent = new Headers(fetch.mock.calls[1][1].headers);
    expect(sent.get("traceparent")).toBeNull();
    expect(sent.get("tracestate")).toBeNull();
  });

  it("refuses writes from another origin with a translated problem", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    const response = await proxy(
      request("api/v1/stations", {
        method: "POST",
        body: "{}",
        headers: { origin: "https://evil.example", "accept-language": "en" },
      }),
      "api/v1/stations",
    );
    expect(response.status).toBe(403);
    expect(response.headers.get("content-type")).toContain("application/problem+json");
    const problem = await response.json();
    expect(problem.title).toBe("Forbidden");
    expect(problem.detail).not.toContain("Origine");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("forwards same-origin writes with their body", async () => {
    const fetch = vi.fn().mockResolvedValue(upstream({ status: 201 }));
    vi.stubGlobal("fetch", fetch);
    const response = await proxy(
      request("api/v1/stations", {
        method: "POST",
        body: '{"name":"CS"}',
        headers: {
          origin: "https://app.example",
          "content-type": "application/json",
          "x-xsrf-token": "t",
        },
      }),
      "api/v1/stations",
    );
    expect(response.status).toBe(201);
    const [, init] = fetch.mock.calls[0];
    expect(new TextDecoder().decode(init.body)).toBe('{"name":"CS"}');
    expect(new Headers(init.headers).get("x-xsrf-token")).toBe("t");
  });

  it("lets signed Stripe webhooks through without an origin", async () => {
    const fetch = vi.fn().mockResolvedValue(upstream({ status: 200, headers: [] }, null));
    vi.stubGlobal("fetch", fetch);
    const response = await proxy(
      request("api/v1/stripe/webhook", {
        method: "POST",
        body: "{}",
        headers: { "stripe-signature": "sig" },
      }),
      "api/v1/stripe/webhook",
    );
    expect(response.status).toBe(200);
    expect(new Headers(fetch.mock.calls[0][1].headers).get("stripe-signature")).toBe("sig");
  });

  it("rejects oversized bodies", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    const declared = await proxy(
      request("api/v1/stations", {
        method: "POST",
        body: "{}",
        headers: { origin: "https://app.example", "content-length": "2000000" },
      }),
      "api/v1/stations",
    );
    expect(declared.status).toBe(413);
    const actual = await proxy(
      request("api/v1/stations", {
        method: "POST",
        body: "x".repeat(1_048_577),
        headers: { origin: "https://app.example" },
      }),
      "api/v1/stations",
    );
    expect(actual.status).toBe(413);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("answers 502 in French when the server is unreachable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("connect ECONNREFUSED")));
    const response = await proxy(request("api/v1/stations"), "api/v1/stations");
    expect(response.status).toBe(502);
    const problem = await response.json();
    expect(problem.title).toBe("Bad Gateway");
    expect(problem.detail).toContain("indisponible");
  });
});
