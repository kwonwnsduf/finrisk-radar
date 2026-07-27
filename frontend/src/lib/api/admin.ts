import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export interface AdminPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface MoneySummary { currency: string; count: number; amount: number }
export interface AdminDashboard {
  asOf: string;
  zoneId: string;
  users: {
    total: number; free: number; premium: number; activeSubscriptions: number;
    newLast24Hours: number; newLast7Days: number; newSubscriptionRecordsLast7Days: number;
  };
  payments: {
    approvedLast24Hours: MoneySummary[]; approvedLast7Days: MoneySummary[];
    failedAttemptsLast24Hours: number; failedAttemptsLast7Days: number;
    canceledLast7Days: MoneySummary[]; recoveryRequired: number; unresolvedFsd: number;
  };
  jobs: {
    activeBacktests: number; failedBacktestsLast24Hours: number;
    activeReports: number; failedReportsLast24Hours: number; staleReports: number;
    failedMarketCollectionsLast24Hours: number; failedDocumentCollectionsLast24Hours: number;
  };
  reviews: {
    openFsd: number; reviewingFsd: number; pendingCandidates: number;
    pendingCandidateAssets: number; newCandidatesLast24Hours: number;
  };
}
export interface AdminUser {
  userId: number; email: string; name: string; role: string; plan: string; joinedAt: string;
  activeSubscription: boolean; subscriptionEndAt: string | null;
  latestPaymentOrderId: string | null; latestPaymentStatus: string | null; latestPaymentAt: string | null;
}
export interface AdminSubscription {
  subscriptionId: number; userId: number; email: string | null; name: string | null;
  plan: string; status: string; currentPeriodStart: string | null; currentPeriodEnd: string | null;
  publicOrderId: string | null;
}
export interface AdminPayment {
  orderId: string; userId: number; email: string | null; name: string | null;
  productCode: string; orderName: string; amount: number; currency: string; status: string;
  createdAt: string; paidAt: string | null; canceledAt: string | null;
  latestFailureCode: string | null; fsdStatus: string | null; fsdSeverity: string | null;
  recoveryRequired: boolean;
}
export interface AdminPaymentAttempt {
  attemptType: string; result: string; errorCode: string | null; errorMessage: string | null;
  createdAt: string; completedAt: string | null;
}
export interface AdminPaymentDetail {
  payment: AdminPayment;
  attempts: AdminPaymentAttempt[];
  cancellation: null | {
    cancelReason: string; amount: number; status: string; requestedAt: string;
    completedAt: string | null; failedAt: string | null;
  };
  latestReconciliation: AdminPaymentAttempt | null;
}
export interface AdminIssue {
  issueType: string; jobId: string; userId: number | null; email: string | null;
  assetId: number | null; assetName: string | null; ticker: string | null; status: string;
  requestedAt: string | null; startedAt: string | null; completedAt: string | null;
  updatedAt: string | null; failureCode: string | null; failureMessage: string | null; ageSeconds: number;
}
type Params = Record<string, string | number | boolean | undefined | null>;
function clean(params: Params) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null));
}
async function get<T>(url: string, params?: Params) {
  const response = await apiClient.get<ApiResponse<T>>(url, { params: params ? clean(params) : undefined });
  return response.data.data;
}
export const adminKeys = {
  all: ["admin"] as const,
  dashboard: ["admin", "dashboard"] as const,
  users: (params: Params) => ["admin", "users", params] as const,
  subscriptions: (params: Params) => ["admin", "subscriptions", params] as const,
  payments: (params: Params) => ["admin", "payments", params] as const,
  payment: (orderId: string) => ["admin", "payments", orderId] as const,
  issues: (kind: string, page: number) => ["admin", "issues", kind, page] as const,
};
export const getAdminDashboard = () => get<AdminDashboard>("/api/admin/dashboard");
export const getAdminUsers = (params: Params) => get<AdminPage<AdminUser>>("/api/admin/users", params);
export const getAdminSubscriptions = (params: Params) => get<AdminPage<AdminSubscription>>("/api/admin/subscriptions", params);
export const getAdminPayments = (params: Params) => get<AdminPage<AdminPayment>>("/api/admin/payments", params);
export const getAdminPayment = (orderId: string) => get<AdminPaymentDetail>(`/api/admin/payments/${orderId}`);
export async function reconcileAdminPayment(orderId: string) {
  const response = await apiClient.post<ApiResponse<{ orderId: string; result: string }>>(
    `/api/admin/payments/${orderId}/reconcile`,
  );
  return response.data.data;
}
export function getAdminIssues(kind: "backtests" | "reports" | "MARKET_DATA" | "DOCUMENT", page: number) {
  return kind === "backtests" || kind === "reports"
    ? get<AdminPage<AdminIssue>>(`/api/admin/operational-issues/${kind}`, { page, size: 20 })
    : get<AdminPage<AdminIssue>>("/api/admin/operational-issues/collections", { kind, page, size: 20 });
}
