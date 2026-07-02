import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export async function getBackendHealth() {
  const response = await apiClient.get<ApiResponse<string>>("/api/health");
  return response.data;
}
