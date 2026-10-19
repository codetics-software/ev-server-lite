import { NextRequest, NextResponse } from "next/server";
export async function proxy(request: NextRequest, path: string) {
  if (
    !/^(api\/v1\/|oauth2\/authorization\/|login\/oauth2\/code\/)/.test(path) ||
    path.includes("..")
  ) {
    return new NextResponse(null, { status: 404 });
  }
  const target = new URL(`/${path}`, process.env.EV_SERVER_URL || "http://127.0.0.1:8080");
  target.search = request.nextUrl.search;
  const headers = new Headers();
  for (const key of [
    "cookie",
    "content-type",
    "accept",
    "x-xsrf-token",
    "x-charge-token",
    "stripe-signature",
  ]) {
    const value = request.headers.get(key);
    if (value) {
      headers.set(key, value);
    }
  }
  const isMutation = !["GET", "HEAD"].includes(request.method);
  if (isMutation && !["login/oauth2/code/apple", "api/v1/stripe/webhook"].includes(path)) {
    const allowed = new URL(process.env.APP_PUBLIC_URL || request.nextUrl.origin).origin;
    if (request.headers.get("origin") !== allowed) {
      return NextResponse.json({ detail: "Origine de la demande non autorisée." }, { status: 403 });
    }
  }
  try {
    if (Number(request.headers.get("content-length") || 0) > 1_048_576) {
      return new NextResponse(null, { status: 413 });
    }
    const body = isMutation ? await request.arrayBuffer() : undefined;
    if (body && body.byteLength > 1_048_576) {
      return new NextResponse(null, { status: 413 });
    }
    if (process.env.EV_LOCAL_TEST_CLASSPATH && process.env.NODE_ENV !== "production") {
      const { localTestRequest } = await import("@/lib/local-test-bridge");
      const result = await localTestRequest({
        method: request.method,
        path: target.pathname + target.search,
        headers: Object.fromEntries(headers.entries()),
        body: body ? Buffer.from(body).toString("utf8") : null,
      });
      const response = new NextResponse(
        [204, 205, 304].includes(result.status) ? null : result.body,
        { status: result.status },
      );

      for (const [key, values] of Object.entries(result.headers)) {
        if (["content-type", "set-cookie", "location"].includes(key.toLowerCase())) {
          for (const value of values) {
            response.headers.append(key, value);
          }
        }
      }

      response.headers.set("Cache-Control", "no-store");
      return response;
    }
    const upstream = await fetch(target, {
      method: request.method,
      headers,
      body,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(20_000),
    });
    const response = new NextResponse(upstream.body, {
      status: upstream.status,
    });
    for (const key of ["content-type", "location", "retry-after"]) {
      const value = upstream.headers.get(key);
      if (value) {
        response.headers.set(key, value);
      }
    }
    for (const cookie of upstream.headers.getSetCookie()) {
      response.headers.append("set-cookie", cookie);
    }
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch {
    return NextResponse.json(
      {
        detail: "Le serveur de supervision est indisponible. Vérifiez la connexion et réessayez.",
      },
      { status: 502 },
    );
  }
}
