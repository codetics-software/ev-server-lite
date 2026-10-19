export type { PageData, Row, User } from "./api-types";
export { date, display } from "./format";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method || "GET";
  const headers = new Headers(options.headers);
  if (options.body) {
    headers.set("Content-Type", "application/json");
  }
  if (!["GET", "HEAD"].includes(method)) {
    const response = await fetch("/api/backend/api/v1/auth/csrf", {
      cache: "no-store",
      credentials: "same-origin",
    });
    if (!response.ok) {
      throw new ApiError(response.status, "Unable to establish a secure session");
    }
    const csrf = await response.json();
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(`/api/backend/api/v1/${path}`, {
    ...options,
    headers,
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      problem?.detail ||
        (response.status === 403
          ? "Your role does not allow this action."
          : "The request could not be completed."),
    );
  }
  return response.json();
}
