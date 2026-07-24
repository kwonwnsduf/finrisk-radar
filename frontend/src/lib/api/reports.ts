import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";
export type ReportType =
  | "RISK_ANALYSIS"
  | "BACKTEST_ANALYSIS"
  | "WATCHLIST_SUMMARY";
export type ReportStatus = "REQUESTED" | "RUNNING" | "COMPLETED" | "FAILED";
export type ReportStep =
  | "ASSET_RESOLUTION"
  | "RISK_DATA"
  | "DOCUMENT_SEARCH"
  | "AI_ANALYSIS"
  | "REPORT_SAVE"
  | "COMPLETED";
export interface ReportAccepted {
  reportId: string;
  status: ReportStatus;
  currentStep: ReportStep;
  reused: boolean;
}
export interface ReportItem {
  id: string;
  assetId: number | null;
  backtestJobId: string | null;
  reportType: ReportType;
  status: ReportStatus;
  currentStep: ReportStep | null;
  question: string | null;
  title: string | null;
  content: string | null;
  structuredResult: Record<string, unknown> | null;
  model: string | null;
  promptVersion: string;
  inputTokenCount: number;
  outputTokenCount: number;
  failureCode: string | null;
  failureMessage: string | null;
  requestedAt: string;
  completedAt: string | null;
}
export interface ReportPage {
  items: ReportItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
async function post(path: string, body: unknown) {
  const response = await apiClient.post<ApiResponse<ReportAccepted>>(
    path,
    body,
    { headers: { "Idempotency-Key": crypto.randomUUID() } },
  );
  return response.data.data;
}
export const createRiskReport = (
  assetId: number | undefined,
  question: string,
) => post("/api/reports/risk", { assetId, question });
export const createBacktestReport = (
  backtestJobId: string,
  question?: string,
) => post("/api/reports/backtest", { backtestJobId, question });
export const createWatchlistSummary = (question?: string) =>
  post("/api/reports/watchlist-summary", { question });
export async function getReports(
  params: {
    reportType?: ReportType;
    status?: ReportStatus;
    page?: number;
  } = {},
) {
  const r = await apiClient.get<ApiResponse<ReportPage>>("/api/reports", {
    params,
  });
  return r.data.data;
}
export async function getReport(id: string) {
  const r = await apiClient.get<ApiResponse<ReportItem>>(`/api/reports/${id}`);
  return r.data.data;
}
