import { apiClient } from "@/lib/api/client";
import type { Asset, AssetType } from "@/lib/api/assets";
import type { ApiResponse } from "@/lib/auth/types";

export interface WatchlistItem extends Omit<Asset, "id"> {
  watchlistId: number;
  assetId: number;
  assetType: AssetType;
  createdAt: string;
}

export async function addWatchlist(assetId: number) {
  const response = await apiClient.post<ApiResponse<WatchlistItem>>("/api/watchlists", { assetId });
  return response.data.data;
}

export async function getWatchlists() {
  const response = await apiClient.get<ApiResponse<WatchlistItem[]>>("/api/watchlists");
  return response.data.data;
}

export async function deleteWatchlist(watchlistId: number) {
  await apiClient.delete(`/api/watchlists/${watchlistId}`);
}
