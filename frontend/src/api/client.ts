import type {
  AccountResponse,
  AmountRequest,
  ApiCall,
  ApiErrorPayload,
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

interface RequestOptions {
  method: "GET" | "POST";
  path: string;
  body?: unknown;
  idempotencyKey?: string;
  requestId?: string;
}

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

async function apiRequest<T>(options: RequestOptions): Promise<ApiCall<T>> {
  const url = normalizePath(options.path);
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

  const response = await fetch(url, {
    method: options.method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined
  });

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
    return apiRequest<HealthResponse>({ method: "GET", path: "/health", requestId });
  }
};
