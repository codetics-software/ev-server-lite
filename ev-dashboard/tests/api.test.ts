import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  NETWORK_ERROR_STATUS,
  api,
  errorFromResponse,
  errorMessage,
  query,
} from "@/lib/api";

const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });

describe("api client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("builds query strings without empty values", () => {
    expect(query({ a: 1, b: "", c: null, d: undefined, e: true })).toBe("?a=1&e=true");
    expect(query({})).toBe("");
  });

  it("keeps readable problem details", async () => {
    const error = await errorFromResponse(
      json({ detail: "Borne introuvable.", type: "about:blank" }, 404, { "Retry-After": "5" }),
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.message).toBe("Borne introuvable.");
    expect(error.retryAfter).toBe(5);
    expect(error.type).toBe("about:blank");
  });

  it("hides technical details behind a fallback message", async () => {
    const stack = await errorFromResponse(json({ detail: "java.lang.IllegalStateException" }, 400));
    expect(stack.message).toBe("La demande est invalide.");
    const server = await errorFromResponse(json({ detail: "boom" }, 500));
    expect(server.message).toContain("erreur");
    const empty = await errorFromResponse(new Response("not json", { status: 418 }));
    expect(empty.message).toBe("La demande n’a pas pu aboutir.");
    const framework = await errorFromResponse(
      json({ detail: "Required request body is missing" }, 400),
    );
    expect(framework.message).toBe("La demande est invalide.");
  });

  it("sends GET requests with the active language", async () => {
    const fetch = vi.fn().mockResolvedValue(json({ ok: true }));
    vi.stubGlobal("fetch", fetch);
    await expect(api<{ ok: boolean }>("stations")).resolves.toEqual({ ok: true });
    const [url, init] = fetch.mock.calls[0];
    expect(url).toBe("/api/backend/api/v1/stations");
    expect(new Headers(init.headers).get("Accept-Language")).toBe("fr");
  });

  it("adds the CSRF header on writes", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "abc" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);
    await expect(api("stations/CP1", { method: "DELETE" })).resolves.toBeUndefined();
    const [, init] = fetch.mock.calls[1];
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("abc");
  });

  it("sets a JSON content type on bodies", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "abc" }))
      .mockResolvedValueOnce(json({ id: 1 }));
    vi.stubGlobal("fetch", fetch);
    await api("stations", { method: "POST", body: "{}" });
    const [, init] = fetch.mock.calls[1];
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
  });

  it("fails when the CSRF token cannot be obtained", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({}, 200)));
    await expect(api("stations", { method: "POST" })).rejects.toBeInstanceOf(ApiError);
  });

  it("maps network failures and unreadable bodies", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
    await expect(api("stations")).rejects.toMatchObject({ status: NETWORK_ERROR_STATUS });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{bad", { status: 200 })));
    await expect(api("stations")).rejects.toMatchObject({ status: 200 });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ detail: "Conflit." }, 409)));
    await expect(api("stations")).rejects.toMatchObject({ status: 409, message: "Conflit." });
  });

  it("propagates aborts untouched", async () => {
    const abort = new DOMException("aborted", "AbortError");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(abort));
    await expect(api("stations")).rejects.toBe(abort);
  });

  it("extracts error messages", () => {
    expect(errorMessage(new Error("x"), "fallback")).toBe("x");
    expect(errorMessage("x", "fallback")).toBe("fallback");
  });
});
