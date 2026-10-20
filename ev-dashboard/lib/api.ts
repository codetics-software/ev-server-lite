import type { ProblemDetail } from "./api-types";

export type { PageData, ProblemDetail, Row, User } from "./api-types";
export { date, display } from "./format";
export const NETWORK_ERROR_STATUS = 0;

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public retryAfter: number | null = null,
    public type: string | null = null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const fallbackMessages: Record<number, string> = {
  [NETWORK_ERROR_STATUS]: "Le serveur est injoignable. Vérifiez votre connexion et réessayez.",
  400: "La demande est invalide.",
  401: "Votre session a expiré. Reconnectez-vous.",
  403: "Votre rôle ne permet pas cette action.",
  404: "L’élément demandé est introuvable.",
  409: "Cette opération entre en conflit avec un élément existant.",
  413: "La demande est trop volumineuse.",
  429: "Trop de tentatives. Réessayez dans quelques instants.",
  502: "Un service externe est momentanément indisponible. Réessayez dans quelques instants.",
  503: "Le service est momentanément indisponible. Réessayez dans quelques instants.",
  504: "Le serveur a mis trop de temps à répondre. Réessayez dans quelques instants.",
};

function fallbackMessage(status: number) {
  return (
    fallbackMessages[status] ??
    (status >= 500
      ? "Le serveur a rencontré une erreur. Réessayez plus tard."
      : "La demande n’a pas pu aboutir.")
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
function asProblem(value: unknown): ProblemDetail {
  if (!isRecord(value)) {
    return {};
  }
  const text = (key: string) => (typeof value[key] === "string" ? value[key] : undefined);
  return {
    type: text("type"),
    title: text("title"),
    status: typeof value.status === "number" ? value.status : undefined,
    detail: text("detail"),
    instance: text("instance"),
  };
}
function readableDetail(detail: string | undefined, status: number) {
  const trimmed = detail?.trim();
  if (!trimmed || trimmed.length > 500 || status === 500) {
    return null;
  }
  if (/\n\s*at\s|(?:java|kotlin|org\.springframework|com\.stripe)\.|Exception\b/.test(trimmed)) {
    return null;
  }
  if (
    /^(No (static resource|endpoint|handler)|Request method|Required|Failed to|Content-Type)/i.test(
      trimmed,
    )
  ) {
    return null;
  }
  return trimmed;
}

function retryAfterSeconds(response: Response) {
  const value = Number(response.headers.get("retry-after"));
  return Number.isFinite(value) && value > 0 ? value : null;
}

export async function errorFromResponse(response: Response): Promise<ApiError> {
  const body: unknown = await response.json().catch(() => null);
  const problem = asProblem(body);
  return new ApiError(
    response.status,
    readableDetail(problem.detail, response.status) ?? fallbackMessage(response.status),
    retryAfterSeconds(response),
    problem.type ?? null,
  );
}

function isAbort(error: unknown) {
  return error instanceof DOMException && error.name === "AbortError";
}
async function send(input: string, init: RequestInit) {
  try {
    return await fetch(input, init);
  } catch (error) {
    if (isAbort(error)) {
      throw error;
    }
    throw new ApiError(NETWORK_ERROR_STATUS, fallbackMessage(NETWORK_ERROR_STATUS));
  }
}

async function csrfHeader(signal: AbortSignal | null | undefined) {
  const response = await send("/api/backend/api/v1/auth/csrf", {
    cache: "no-store",
    credentials: "same-origin",
    signal,
  });
  const body: unknown = response.ok ? await response.json().catch(() => null) : null;
  if (!isRecord(body) || typeof body.headerName !== "string" || typeof body.token !== "string") {
    throw new ApiError(response.status, "Impossible d’établir une session sécurisée.");
  }
  return { name: body.headerName, token: body.token };
}

async function request(path: string, options: RequestInit): Promise<Response> {
  const method = options.method || "GET";
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!["GET", "HEAD"].includes(method)) {
    const csrf = await csrfHeader(options.signal);
    headers.set(csrf.name, csrf.token);
  }
  const response = await send(`/api/backend/api/v1/${path}`, {
    ...options,
    headers,
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!response.ok) {
    throw await errorFromResponse(response);
  }
  return response;
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await request(path, options);
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(response.status, "La réponse du serveur est illisible.");
  }
}

function filenameFrom(response: Response, fallback: string) {
  const header = response.headers.get("content-disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  return match ? decodeURIComponent(match[1]) : fallback;
}

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function download(path: string, filename: string) {
  const response = await request(path, { method: "GET", headers: { Accept: "text/csv" } });
  saveBlob(await response.blob(), filenameFrom(response, filename));
}

export async function upload<T>(
  path: string,
  body: BodyInit,
  contentType: string,
  method = "POST",
) {
  return api<T>(path, { method, body, headers: { "Content-Type": contentType } });
}

export function query(parameters: Record<string, string | number | boolean | null | undefined>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value !== null && value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}
