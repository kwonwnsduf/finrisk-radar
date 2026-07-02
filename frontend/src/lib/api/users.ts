import { apiClient } from "@/lib/api/client";
import type { ApiResponse, AuthUser } from "@/lib/auth/types";

export async function getCurrentUser() {
  const response = await apiClient.get<ApiResponse<AuthUser>>("/api/users/me");
  return response.data.data;
}
