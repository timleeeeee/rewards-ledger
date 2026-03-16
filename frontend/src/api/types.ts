export type AccountStatus = "ACTIVE" | "SUSPENDED" | "CLOSED";

export type TransactionType =
  | "EARN"
  | "SPEND"
  | "TRANSFER_IN"
  | "TRANSFER_OUT"
  | "REVERSAL";

export type EntryDirection = "CREDIT" | "DEBIT";

export interface CreateAccountResponse {
  id: string;
  status: AccountStatus;
  createdAt: string;
}

export interface AccountResponse {
  id: string;
  status: AccountStatus;
  balance: number;
  createdAt: string;
}

export interface AmountRequest {
  amount: number;
  reason?: string;
  currency?: string;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  reason?: string;
  currency?: string;
}

export interface ReversalRequest {
  originalTransactionId: string;
  reason?: string;
}

export interface TransactionResponse {
  id: string;
  accountId: string;
  type: TransactionType;
  direction: EntryDirection;
  amount: number;
  currency: string;
  idempotencyKey: string;
  relatedAccountId?: string | null;
  referenceTransactionId?: string | null;
  reason?: string | null;
  createdAt: string;
}

export interface TransactionPageResponse {
  items: TransactionResponse[];
  nextCursor?: string | null;
}

export interface HealthResponse {
  status: string;
  version: string;
  checks: Record<string, string>;
}

export interface AuthUser {
  id: string;
  email: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresInSeconds: number;
  refreshToken: string;
  user: AuthUser;
}

export interface FieldValidationError {
  field: string;
  message: string;
}

export interface ApiErrorPayload {
  code: string;
  message: string;
  requestId?: string;
  timestamp?: string;
  fieldErrors?: FieldValidationError[];
}

export interface ApiCall<T> {
  data: T;
  status: number;
  requestId: string | null;
  method: string;
  path: string;
}
