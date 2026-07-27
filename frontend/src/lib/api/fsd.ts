import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export interface FsdEvent {
  id: number;
  orderId?: string;
  userId: number;
  ruleCode: string;
  phase: string;
  decision: "ALLOW" | "REVIEW" | "BLOCK";
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  score: number;
  reason: string;
  evidence: Record<string, unknown>;
  status: "OPEN" | "REVIEWING" | "RESOLVED" | "FALSE_POSITIVE";
  detectedAt: string;
  reviewedAt?: string;
  reviewedBy?: number;
  reviewNote?: string;
}

export interface FsdPage {
  items: FsdEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function getFsdEvents(params: Record<string, string | number>) {
  const filtered = Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== ""),
  );
  const response = await apiClient.get<ApiResponse<FsdPage>>(
    "/api/admin/fsd-events",
    { params: filtered },
  );
  return response.data.data;
}

export async function reviewFsdEvent(
  id: number,
  status: FsdEvent["status"],
  reviewNote: string,
) {
  const response = await apiClient.patch<ApiResponse<FsdEvent>>(
    `/api/admin/fsd-events/${id}`,
    { status, reviewNote },
  );
  return response.data.data;
}
