import axios from "axios";

import { apiClient } from "@/lib/api/client";
import type {
  ApiResponse,
  AuthResponse,
  LoginRequest,
  SignupRequest,
  SignupResponse,
} from "@/lib/auth/types";

export async function login(request: LoginRequest) {
  const response = await apiClient.post<ApiResponse<AuthResponse>>(
    "/api/auth/login",
    request,
  );
  return response.data.data;
}

export async function signup(request: SignupRequest) {
  const response = await apiClient.post<ApiResponse<SignupResponse>>(
    "/api/auth/signup",
    request,
  );
  return response.data.data;
}

export async function exchangeOAuthCode(code: string) {
  const response = await apiClient.post<ApiResponse<AuthResponse>>(
    "/api/auth/oauth/exchange",
    { code },
  );
  return response.data.data;
}

export async function logout() {
  await apiClient.post<ApiResponse<null>>("/api/auth/logout");
}

export function getAuthErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) {
    return fallback;
  }

  const message = (error.response?.data as Partial<ApiResponse<unknown>> | undefined)
    ?.message;
  return typeof message === "string" && message.length > 0 ? message : fallback;
}
