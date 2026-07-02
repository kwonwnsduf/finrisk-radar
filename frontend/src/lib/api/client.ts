import axios, {
  AxiosHeaders,
  type InternalAxiosRequestConfig,
} from "axios";

import { clearTokens, getTokens, updateAccessToken } from "@/lib/auth/token-storage";
import type { ApiResponse, TokenResponse } from "@/lib/auth/types";
import { useAuthStore } from "@/store/auth-store";
import { announceUsageLimit } from "@/lib/api/limit-errors";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "/backend-api";

export const authRefreshClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  timeout: 5_000,
});

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 5_000,
});

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const AUTH_REFRESH_EXCLUDED_PATHS = [
  "/api/auth/login",
  "/api/auth/signup",
  "/api/auth/oauth/exchange",
  "/api/auth/refresh",
];

let refreshPromise: Promise<string> | null = null;

function isRefreshExcluded(url?: string) {
  return AUTH_REFRESH_EXCLUDED_PATHS.some((path) => url?.includes(path));
}

function redirectToLogin() {
  clearTokens();
  useAuthStore.getState().setAnonymous();
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.replace("/login");
  }
}

async function refreshAccessToken() {
  const tokens = getTokens();
  if (!tokens) {
    throw new Error("Refresh token is unavailable.");
  }

  if (!refreshPromise) {
    refreshPromise = authRefreshClient
      .post<ApiResponse<TokenResponse>>("/api/auth/refresh", {
        refreshToken: tokens.refreshToken,
      })
      .then((response) => {
        const accessToken = response.data.data.accessToken;
        updateAccessToken(accessToken);
        return accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

apiClient.interceptors.request.use((config) => {
  if (isRefreshExcluded(config.url)) {
    return config;
  }

  const accessToken = getTokens()?.accessToken;
  if (accessToken) {
    config.headers = AxiosHeaders.from(config.headers);
    config.headers.set("Authorization", `Bearer ${accessToken}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error);
    }

    const config = error.config as RetryableRequestConfig | undefined;
    if (error.response?.status === 429) {
      const responseData = error.response.data as { code?: string } | undefined;
      announceUsageLimit(responseData?.code);
    }
    if (
      error.response?.status !== 401 ||
      !config ||
      config._retry ||
      isRefreshExcluded(config.url)
    ) {
      return Promise.reject(error);
    }

    config._retry = true;
    try {
      const accessToken = await refreshAccessToken();
      config.headers = AxiosHeaders.from(config.headers);
      config.headers.set("Authorization", `Bearer ${accessToken}`);
      return apiClient(config);
    } catch (refreshError) {
      redirectToLogin();
      return Promise.reject(refreshError);
    }
  },
);
