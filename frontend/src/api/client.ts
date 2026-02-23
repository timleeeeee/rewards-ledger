import { clearSession, getSessionState, isAccessTokenExpired, setSessionFromAuth } from "./session";
import type {
  AccountResponse,
  AmountRequest,
  ApiCall,
  ApiErrorPayload,
  AuthResponse,
  AuthUser,
  CreateAccountResponse,
  HealthResponse,
  ReversalRequest,
  TransactionPageResponse,
  TransactionResponse,
  TransferRequest
} from "./types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "/api";

export class ApiHttpError extends Error {
  status: number;
  payload: ApiErrorPayload | null;
  requestId: string | null;
  method: string;
  path: string;

  constructor(
    message: string,
    status: number,
    payload: ApiErrorPayload | null,
    requestId: string | null,
    method: string,
    path: string
  ) {
    super(message);
    this.name = "ApiHttpError";
    this.status = status;
    this.payload = payload;
    this.requestId = requestId;
    this.method = method;
    this.path = path;
  }
}

type HttpMethod = "GET" | "POST";

interface RequestOptions {
  method: HttpMethod;
  path: string;
  body?: unknown;
  idempotencyKey?: string;
  requestId?: string;
  includeAuth?: boolean;
}

let refreshInFlight: Promise<boolean> | null = null;

function normalizePath(path: string): string {
  const base = API_BASE_URL.endsWith("/") ? API_BASE_URL.slice(0, -1) : API_BASE_URL;
  const suffix = path.startsWith("/") ? path : `/${path}`;
  return `${base}${suffix}`;
}

async function parseJsonSafe(text: string): Promise<unknown> {
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function refreshAccessToken(): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const session = getSessionState();
  if (!session?.refreshToken) {
    return false;
  }

  refreshInFlight = (async () => {
    const response = await fetch(normalizePath("/auth/refresh"), {
      method: "POST",
      headers: {
        "Accept": "application/json",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ refreshToken: session.refreshToken })
    });

    const responseText = await response.text();
    const parsed = (await parseJsonSafe(responseText)) as AuthResponse | null;
    if (!response.ok || !parsed?.accessToken || !parsed?.refreshToken) {
      clearSession();
      return false;
    }

    setSessionFromAuth(parsed);
    return true;
  })()
    .catch(() => {
      clearSession();
      return false;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

function shouldTryRefresh(path: string, includeAuth: boolean): boolean {
  if (!includeAuth) {
    return false;
  }
  return path !== "/auth/login" && path !== "/auth/register" && path !== "/auth/refresh";
}

async function sendRequest(options: RequestOptions, alreadyRetried = false): Promise<Response> {
  const includeAuth = options.includeAuth !== false;
  const session = getSessionState();

  if (includeAuth && session && isAccessTokenExpired(session) && !alreadyRetried) {
    await refreshAccessToken();
  }

  const latestSession = getSessionState();
  const headers = new Headers();
  headers.set("Accept", "application/json");

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (options.idempotencyKey) {
    headers.set("Idempotency-Key", options.idempotencyKey);
  }
  if (options.requestId) {
    headers.set("X-Request-Id", options.requestId);
  }
  if (includeAuth && latestSession?.accessToken) {
    headers.set("Authorization", `Bearer ${latestSession.accessToken}`);
  }

  const response = await fetch(normalizePath(options.path), {
    method: options.method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined
  });

  if (
    response.status === 401 &&
    !alreadyRetried &&
    shouldTryRefresh(options.path, includeAuth) &&
    (await refreshAccessToken())
  ) {
    return sendRequest(options, true);
  }

  return response;
}

async function apiRequest<T>(options: RequestOptions): Promise<ApiCall<T>> {
  const response = await sendRequest(options);
  const responseText = await response.text();
  const parsed = (await parseJsonSafe(responseText)) as T | ApiErrorPayload | null;
  const responseRequestId = response.headers.get("X-Request-Id");

  if (!response.ok) {
    const payload = parsed && typeof parsed === "object" ? (parsed as ApiErrorPayload) : null;
    const message = payload?.message ?? `Request failed with status ${response.status}`;
    throw new ApiHttpError(
      message,
      response.status,
      payload,
      responseRequestId,
      options.method,
      options.path
    );
  }

  return {
    data: parsed as T,
    status: response.status,
    requestId: responseRequestId,
    method: options.method,
    path: options.path
  };
}

export const ledgerApi = {
  register(email: string, password: string, requestId?: string) {
    return apiRequest<AuthResponse>({
      method: "POST",
      path: "/auth/register",
      body: { email, password },
      requestId,
      includeAuth: false
    });
  },
  login(email: string, password: string, requestId?: string) {
    return apiRequest<AuthResponse>({
      method: "POST",
      path: "/auth/login",
      body: { email, password },
      requestId,
      includeAuth: false
    });
  },
  logout(refreshToken: string, requestId?: string) {
    return apiRequest<{ status: string }>({
      method: "POST",
      path: "/auth/logout",
      body: { refreshToken },
      requestId
    });
  },
  me(requestId?: string) {
    return apiRequest<AuthUser>({
      method: "GET",
      path: "/auth/me",
      requestId
    });
  },
  createAccount(requestId?: string) {
    return apiRequest<CreateAccountResponse>({ method: "POST", path: "/accounts", requestId });
  },
  getAccount(accountId: string, requestId?: string) {
    return apiRequest<AccountResponse>({ method: "GET", path: `/accounts/${accountId}`, requestId });
  },
  listTransactions(accountId: string, limit = 20, cursor?: string | null, requestId?: string) {
    const query = new URLSearchParams();
    query.set("limit", String(limit));
    if (cursor) {
      query.set("cursor", cursor);
    }
    return apiRequest<TransactionPageResponse>({
      method: "GET",
      path: `/accounts/${accountId}/transactions?${query.toString()}`,
      requestId
    });
  },
  earn(accountId: string, body: AmountRequest, idempotencyKey: string, requestId?: string) {
    return apiRequest<TransactionResponse>({
      method: "POST",
      path: `/accounts/${accountId}/earn`,
      body,
      idempotencyKey,
      requestId
    });
  },
  spend(accountId: string, body: AmountRequest, idempotencyKey: string, requestId?: string) {
    return apiRequest<TransactionResponse>({
      method: "POST",
      path: `/accounts/${accountId}/spend`,
      body,
      idempotencyKey,
      requestId
    });
  },
  transfer(body: TransferRequest, idempotencyKey: string, requestId?: string) {
    return apiRequest<TransactionResponse[]>({
      method: "POST",
      path: "/transfer",
      body,
      idempotencyKey,
      requestId
    });
  },
  reversal(accountId: string, body: ReversalRequest, idempotencyKey: string, requestId?: string) {
    return apiRequest<TransactionResponse>({
      method: "POST",
      path: `/accounts/${accountId}/reversal`,
      body,
      idempotencyKey,
      requestId
    });
  },
  health(requestId?: string) {
    return apiRequest<HealthResponse>({ method: "GET", path: "/health", requestId, includeAuth: false });
  }
};
