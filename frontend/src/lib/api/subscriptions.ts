import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type PlanType = "FREE" | "PREMIUM" | "ADMIN";

export interface SubscriptionResponse {
  plan: PlanType;
}

export async function getMySubscription() {
  const response = await apiClient.get<ApiResponse<SubscriptionResponse>>(
    "/api/subscriptions/me",
  );
  return response.data.data;
}
