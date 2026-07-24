import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type RagDocumentType = "NEWS" | "DISCLOSURE";
export type RagSourceType = "NAVER_NEWS" | "OPEN_DART";
export type RagContentScope = "FULL_TEXT" | "PARTIAL" | "SNIPPET" | "UNUSABLE";

export interface RagSearchRequest {
  query: string;
  assetId?: number;
  documentType?: RagDocumentType;
  sourceType?: RagSourceType;
  publishedFrom?: string;
  publishedTo?: string;
  limit?: number;
  minimumSimilarity?: number;
}

export interface RagSearchResult {
  chunkId: number;
  documentId: number;
  chunkIndex: number;
  assets: { id: number; name: string }[];
  documentTitle: string;
  chunkContent: string;
  similarity: number;
  documentType: RagDocumentType;
  sourceType: RagSourceType;
  contentScope: RagContentScope;
  sourceName: string | null;
  sourceUrl: string | null;
  publishedAt: string | null;
  riskMatches: {
    id: number;
    eventType: string;
    sentenceText: string;
    matchedText: string;
    evidence: string;
  }[];
}

export async function searchRag(request: RagSearchRequest) {
  const response = await apiClient.post<ApiResponse<RagSearchResult[]>>(
    "/api/rag/search",
    request,
  );
  return response.data.data;
}
