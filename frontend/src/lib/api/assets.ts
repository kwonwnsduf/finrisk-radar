import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type AssetType = "STOCK" | "REIT" | "BOND_ISSUER";

export interface Asset {
  id: number;
  name: string;
  ticker: string;
  market: string | null;
  sector: string | null;
  country: string | null;
  currency: string | null;
  assetType: AssetType;
}

export async function getAssets() {
  const response = await apiClient.get<ApiResponse<Asset[]>>("/api/assets");
  return response.data.data;
}

export async function searchAssets(keyword: string, assetType?: AssetType) {
  const response = await apiClient.get<ApiResponse<Asset[]>>("/api/assets/search", {
    params: { keyword, assetType },
  });
  return response.data.data;
}

export async function getAsset(assetId: number) {
  const response = await apiClient.get<ApiResponse<Asset>>(`/api/assets/${assetId}`);
  return response.data.data;
}
