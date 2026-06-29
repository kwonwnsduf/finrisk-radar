import { apiClient } from "@/lib/api/client";

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export async function getBackendHealth() {
  const response = await apiClient.get<ApiResponse<string>>("/api/health");
  return response.data;
}
