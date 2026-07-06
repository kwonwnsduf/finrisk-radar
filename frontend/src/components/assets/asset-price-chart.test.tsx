import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AssetPriceChart } from "@/components/assets/asset-price-chart";
import { fetchMarketPrices, getCollectionJob, getMarketPrices } from "@/lib/api/market-prices";
import { useAuthStore } from "@/store/auth-store";

vi.mock("@/lib/api/market-prices", () => ({
  getMarketPrices: vi.fn(),
  fetchMarketPrices: vi.fn(),
  getCollectionJob: vi.fn(),
}));

const samsung = {
  id: 1,
  name: "삼성전자",
  ticker: "005930",
  market: "KOSPI",
  sector: "Semiconductor",
  country: "KR",
  currency: "KRW",
  assetType: "STOCK" as const,
};

function renderChart() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><AssetPriceChart asset={samsung} /></QueryClientProvider>);
}

describe("AssetPriceChart", () => {
  beforeEach(() => {
    vi.mocked(getMarketPrices).mockResolvedValue([]);
    vi.mocked(fetchMarketPrices).mockResolvedValue({ jobId: "job-id", status: "REQUESTED" });
    vi.mocked(getCollectionJob).mockResolvedValue({
      jobId: "job-id", assetId: 1, ticker: "005930.KS", source: null,
      status: "RUNNING", message: "Collection is running.", rawS3Path: null,
      startedAt: null, completedAt: null,
    });
  });

  it("offers login when prices are empty for an anonymous visitor", async () => {
    renderChart();
    expect(await screen.findByText("선택한 기간의 가격 데이터가 없습니다.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "로그인 후 수집" })).toHaveAttribute("href", "/login");
  });

  it("requests the resolved Yahoo ticker for an authenticated user", async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setAuthenticated({ id: 7, email: "user@example.com", name: "User", role: "ROLE_USER" });
    renderChart();
    await user.click(await screen.findByRole("button", { name: "가격 데이터 수집" }));
    expect(fetchMarketPrices).toHaveBeenCalledWith(expect.objectContaining({ assetId: 1, ticker: "005930.KS" }));
    expect(await screen.findByText(/수집 상태: RUNNING/)).toBeInTheDocument();
  });
});
