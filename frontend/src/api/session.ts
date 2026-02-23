import type { AuthResponse, AuthUser } from "./types";

const STORAGE_KEY = "rewards.auth.session";

export interface SessionState {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAtEpochMs: number;
  user: AuthUser;
}

function parseStored(raw: string | null): SessionState | null {
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as SessionState;
    if (
      typeof parsed.accessToken !== "string" ||
      typeof parsed.refreshToken !== "string" ||
      typeof parsed.accessTokenExpiresAtEpochMs !== "number" ||
      typeof parsed.user?.id !== "string" ||
      typeof parsed.user?.email !== "string"
    ) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function getSessionState(): SessionState | null {
  return parseStored(window.localStorage.getItem(STORAGE_KEY));
}

export function setSessionFromAuth(response: AuthResponse): SessionState {
  const state: SessionState = {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    accessTokenExpiresAtEpochMs: Date.now() + response.accessTokenExpiresInSeconds * 1000,
    user: response.user
  };
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  return state;
}

export function clearSession(): void {
  window.localStorage.removeItem(STORAGE_KEY);
}

export function isAccessTokenExpired(session: SessionState, leewaySeconds = 15): boolean {
  const now = Date.now();
  return (session.accessTokenExpiresAtEpochMs - now) <= leewaySeconds * 1000;
}
