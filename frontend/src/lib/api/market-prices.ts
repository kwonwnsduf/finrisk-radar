import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type MarketPriceSource = "YAHOO" | "CSV" | "MANUAL";
export type CollectionStatus = "REQUESTED" | "RUNNING" | "COMPLETED" | "FAILED";

export interface MarketPrice {
  assetId: number;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  source: MarketPriceSource;
}

export interface FetchMarketPricesRequest {
  assetId: number;
  ticker: string;
  startDate: string;
  endDate: string;
}

export interface CollectionJob {
  jobId: string;
  assetId: number;
  ticker: string;
  source: MarketPriceSource | null;
  status: CollectionStatus;
  message: string | null;
  rawS3Path: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export async function getMarketPrices(assetId: number, startDate?: string, endDate?: string) {
  const response = await apiClient.get<ApiResponse<MarketPrice[]>>(`/api/market-prices/${assetId}`, {
    params: { startDate, endDate },
  });
  return response.data.data;
}

export async function fetchMarketPrices(request: FetchMarketPricesRequest) {
  const response = await apiClient.post<ApiResponse<{ jobId: string; status: CollectionStatus }>>(
    "/api/collector/market-prices/fetch",
    request,
  );
  return response.data.data;
}

export async function getCollectionJob(jobId: string) {
  const response = await apiClient.get<ApiResponse<CollectionJob>>(
    `/api/collector/market-prices/jobs/${jobId}`,
  );
  return response.data.data;
}
