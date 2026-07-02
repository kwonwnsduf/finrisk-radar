import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";
import type { PlanType } from "@/lib/api/subscriptions";

export interface UsageItem {
  used: number;
  limit: number | null;
}

export interface UsageResponse {
  plan: PlanType;
  backtest: UsageItem;
  riskReport: UsageItem;
  aiAgent: UsageItem;
  watchlist: UsageItem;
}

export async function getMyUsage() {
  const response = await apiClient.get<ApiResponse<UsageResponse>>(
    "/api/users/me/usage",
  );
  return response.data.data;
}
