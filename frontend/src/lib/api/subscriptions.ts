import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type PlanType = "FREE" | "PREMIUM" | "ADMIN";

export interface Entitlement {
  orderId: string;
  periodStart: string;
  periodEnd: string;
  status: "SCHEDULED" | "ACTIVE" | "CONSUMED" | "CANCELED";
  remainingSeconds: number;
  usedUntil?: string;
  canceledAt?: string;
  removedUnusedSeconds: number;
}

export interface SubscriptionResponse {
  currentPlan: PlanType;
  subscriptionStatus: "ACTIVE" | "CANCELED" | "EXPIRED";
  currentPeriodStart?: string;
  currentPeriodEnd?: string;
  remainingDays: number;
  autoRenew: false;
  activatedByOrderId?: string;
  entitlements: Entitlement[];
}

export async function getMySubscription() {
  const response = await apiClient.get<ApiResponse<SubscriptionResponse>>(
    "/api/subscriptions/me",
  );
  return response.data.data;
}
