import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";
import type { AdminPaymentAttempt, AdminPage } from "@/lib/api/admin";
export interface FsdEvent {
  id: number; orderId: string | null; userId: number; userEmail: string | null; userName: string | null;
  amount: number | null; currency: string | null; paymentStatus: string | null;
  ruleCode: string; phase: string; decision: "ALLOW"|"REVIEW"|"BLOCK";
  severity: "LOW"|"MEDIUM"|"HIGH"|"CRITICAL"; score: number; reason: string;
  evidence: Record<string, unknown>; status: "OPEN"|"REVIEWING"|"RESOLVED"|"FALSE_POSITIVE";
  detectedAt: string; reviewedAt: string | null; reviewedBy: number | null; reviewNote: string | null;
  attempts: AdminPaymentAttempt[];
}
type Params = Record<string, string | number>;
export async function getFsdEvents(params: Params) {
  const response = await apiClient.get<ApiResponse<AdminPage<FsdEvent>>>("/api/admin/fsd-events", { params: Object.fromEntries(Object.entries(params).filter(([,v]) => v !== "")) });
  return response.data.data;
}
export async function getFsdEvent(id: number) {
  const response = await apiClient.get<ApiResponse<FsdEvent>>(`/api/admin/fsd-events/${id}`);
  return response.data.data;
}
export async function reviewFsdEvent(id: number, status: FsdEvent["status"], reviewNote: string) {
  const response = await apiClient.patch<ApiResponse<FsdEvent>>(`/api/admin/fsd-events/${id}`, { status, reviewNote });
  return response.data.data;
}
