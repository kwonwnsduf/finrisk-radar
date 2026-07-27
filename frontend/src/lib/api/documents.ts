import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type DocumentSourceType = "NAVER_NEWS" | "OPEN_DART";
export type AssertionType = "AFFIRMED" | "UNCERTAIN" | "NEGATED";
export interface DocumentItem {
  id: number;
  documentType: "NEWS" | "DISCLOSURE";
  sourceType: DocumentSourceType;
  sourceName: string | null;
  title: string;
  content: string;
  summary: string | null;
  sourceUrl: string | null;
  publishedAt: string | null;
  contentVersion: number;
  assetIds: number[];
  riskMatchCount: number;
}
export interface DocumentRiskMatch {
  id: number;
  assetId: number;
  keywordCode: string;
  eventType: string;
  sentenceIndex: number;
  sentenceText: string;
  matchStartOffset: number;
  matchEndOffset: number;
  matchedText: string;
  assertionType: AssertionType;
  confidence: number;
  extractedAmount: number | null;
  extractedCurrency: string | null;
  amountOriginalText: string | null;
  evidence: string;
  candidateId: number | null;
}
export interface CreditEventCandidate {
  id: number;
  assetId: number;
  assetName: string;
  ticker: string;
  eventType: string;
  eventDate: string;
  severity: string;
  confidence: number;
  documentTitle: string | null;
  documentSource: string | null;
  status: "PENDING_REVIEW" | "APPROVED" | "REJECTED";
  createdAt: string;
}
export interface CreditEventCandidateDetail {
  id: number; assetId: number; assetName: string; ticker: string; eventType: string;
  eventDate: string; severity: string; confidence: number;
  status: "PENDING_REVIEW" | "APPROVED" | "REJECTED";
  reviewedBy: number | null; reviewedAt: string | null; reviewNote: string | null; createdAt: string;
  matches: Array<{
    id: number; documentId: number; documentTitle: string | null; sourceType: string | null;
    sourceName: string | null; sourceUrl: string | null; sentenceText: string; matchedText: string;
    assertionType: AssertionType; confidence: number; extractedAmount: number | null;
    extractedCurrency: string | null; evidence: string;
  }>;
  nearbyCandidates: Array<{ id: number; eventDate: string; severity: string; confidence: number; status: string }>;
}
export interface DocumentCollectionJob {
  jobId: string;
  assetId: number;
  sourceType: DocumentSourceType;
  fromDate: string;
  toDate: string;
  status: "REQUESTED" | "RUNNING" | "COMPLETED" | "FAILED";
  documentCount: number;
  failureCode: string | null;
  failureMessage: string | null;
  requestedAt: string;
  startedAt: string | null;
  completedAt: string | null;
}
export async function getDocuments(assetId: number) {
  const response = await apiClient.get<ApiResponse<DocumentItem[]>>(
    "/api/documents",
    { params: { assetId, size: 20 } },
  );
  return response.data.data;
}
export async function getDocumentMatches(documentId: number) {
  const response = await apiClient.get<ApiResponse<DocumentRiskMatch[]>>(
    `/api/documents/${documentId}/risk-matches`,
  );
  return response.data.data;
}
export async function getCandidates(status = "PENDING_REVIEW", page = 0) {
  const response = await apiClient.get<ApiResponse<{ items: CreditEventCandidate[]; page: number; size: number; totalElements: number; totalPages: number }>>(
    "/api/admin/credit-event-candidates",
    { params: { status, page, size: 20 } },
  );
  return response.data.data;
}
export async function getCandidate(id: number) {
  const response = await apiClient.get<ApiResponse<CreditEventCandidateDetail>>(
    `/api/admin/credit-event-candidates/${id}`,
  );
  return response.data.data;
}
export async function reviewCandidate(
  id: number,
  action: "approve" | "reject",
  reviewNote = "",
) {
  const response = await apiClient.post<ApiResponse<unknown>>(
    `/api/admin/credit-event-candidates/${id}/${action}`,
    { reviewNote },
  );
  return response.data.data;
}
export async function retryCandidateRecalculation(id: number) {
  const response = await apiClient.post<ApiResponse<unknown>>(
    `/api/admin/credit-event-candidates/${id}/recalculate`,
  );
  return response.data.data;
}
export async function requestDocumentCollection(
  assetId: number,
  sourceTypes: DocumentSourceType[],
  fromDate: string,
  toDate: string,
) {
  const response = await apiClient.post<ApiResponse<DocumentCollectionJob[]>>(
    "/api/admin/document-collections",
    { assetIds: [assetId], sourceTypes, fromDate, toDate },
  );
  return response.data.data;
}
export async function getDocumentCollectionJob(jobId: string) {
  const response = await apiClient.get<ApiResponse<DocumentCollectionJob>>(
    `/api/admin/document-collections/${jobId}`,
  );
  return response.data.data;
}
export async function getRecentDocumentCollectionJobs(assetId?: number) {
  const response = await apiClient.get<ApiResponse<DocumentCollectionJob[]>>(
    "/api/admin/document-collections",
    { params: assetId ? { assetId } : undefined },
  );
  return response.data.data;
}
