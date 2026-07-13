import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type RiskJobStatus = "REQUESTED" | "RUNNING" | "COMPLETED" | "FAILED";
export interface RiskJob {
  jobId: string;
  assetId: number;
  status: RiskJobStatus;
  failureCode: string | null;
  failureMessage: string | null;
  ruleVersion: string;
  dataAsOfDate: string;
  riskScoreId: number | null;
}
export interface TopRiskFactor {
  rank: number;
  category: string;
  signalType: string;
  severity: string;
  score: number;
  summary: string;
  evidence: string;
}
export interface RiskScore {
  id: number;
  assetId: number;
  totalScore: number;
  riskGrade: string;
  defaultStatus: string;
  financialScore: number | null;
  liquidityScore: number | null;
  marketScore: number | null;
  creditEventScore: number | null;
  groupContagionScore: number | null;
  categoryStatuses: string;
  dataQuality: string;
  confidence: string;
  requiredRuleSuccessRate: number;
  missingCategories: string;
  usedDataCounts: Record<string, number>;
  dataAsOfDate: string;
  ruleVersion: string;
  calculatedAt: string;
  topRiskFactors: TopRiskFactor[];
}
export async function requestRiskCalculation(assetId: number) {
  const r = await apiClient.post<ApiResponse<RiskJob>>(
    `/api/risks/assets/${assetId}/calculations`,
  );
  return r.data.data;
}
export async function getRiskJob(jobId: string) {
  const r = await apiClient.get<ApiResponse<RiskJob>>(
    `/api/risks/jobs/${jobId}`,
  );
  return r.data.data;
}
export async function getLatestRisk(assetId: number) {
  const r = await apiClient.get<ApiResponse<RiskScore>>(
    `/api/risks/assets/${assetId}/latest`,
  );
  return r.data.data;
}
export async function getRiskScore(id: number) {
  const r = await apiClient.get<ApiResponse<RiskScore>>(`/api/risks/${id}`);
  return r.data.data;
}
export async function getRiskHistory(assetId: number) {
  const r = await apiClient.get<ApiResponse<RiskScore[]>>(
    `/api/risks/assets/${assetId}/history`,
  );
  return r.data.data;
}
