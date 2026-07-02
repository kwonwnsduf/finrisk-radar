import type { AuthTokens } from "@/lib/auth/types";

const TOKEN_STORAGE_KEY = "finrisk-radar.auth.tokens";

function canUseSessionStorage() {
  return typeof window !== "undefined";
}

function isAuthTokens(value: unknown): value is AuthTokens {
  if (!value || typeof value !== "object") {
    return false;
  }

  const candidate = value as Partial<AuthTokens>;
  return (
    typeof candidate.accessToken === "string" &&
    candidate.accessToken.length > 0 &&
    typeof candidate.refreshToken === "string" &&
    candidate.refreshToken.length > 0
  );
}

export function getTokens(): AuthTokens | null {
  if (!canUseSessionStorage()) {
    return null;
  }

  const stored = window.sessionStorage.getItem(TOKEN_STORAGE_KEY);
  if (!stored) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(stored);
    if (isAuthTokens(parsed)) {
      return parsed;
    }
  } catch {
    // Invalid browser state is treated as a signed-out session.
  }

  clearTokens();
  return null;
}

export function storeTokens(tokens: AuthTokens) {
  if (!canUseSessionStorage()) {
    return;
  }
  window.sessionStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(tokens));
}

export function updateAccessToken(accessToken: string) {
  const tokens = getTokens();
  if (!tokens) {
    return;
  }
  storeTokens({ ...tokens, accessToken });
}

export function clearTokens() {
  if (!canUseSessionStorage()) {
    return;
  }
  window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
}
