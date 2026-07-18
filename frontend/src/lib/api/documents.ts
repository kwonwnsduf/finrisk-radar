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
  eventType: string;
  eventDate: string;
  severity: string;
  confidence: number;
  status: "PENDING_REVIEW" | "APPROVED" | "REJECTED";
  approvedCreditEventId: number | null;
  recalculationStatus: string;
  recalculationJobId: string | null;
  matches: DocumentRiskMatch[];
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
export async function getCandidates(status = "PENDING_REVIEW") {
  const response = await apiClient.get<ApiResponse<CreditEventCandidate[]>>(
    "/api/admin/credit-event-candidates",
    { params: { status } },
  );
  return response.data.data;
}
export async function reviewCandidate(
  id: number,
  action: "approve" | "reject",
  reviewNote = "",
) {
  const response = await apiClient.post<ApiResponse<CreditEventCandidate>>(
    `/api/admin/credit-event-candidates/${id}/${action}`,
    { reviewNote },
  );
  return response.data.data;
}
