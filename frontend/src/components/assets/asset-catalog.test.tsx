import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AssetCatalog } from "@/components/assets/asset-catalog";
import { getAssets, searchAssets } from "@/lib/api/assets";
import { addWatchlist } from "@/lib/api/watchlists";

vi.mock("@/lib/api/assets", () => ({ getAssets: vi.fn(), searchAssets: vi.fn() }));
vi.mock("@/lib/api/watchlists", () => ({ addWatchlist: vi.fn() }));

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

function renderCatalog() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><AssetCatalog /></QueryClientProvider>);
}

describe("AssetCatalog", () => {
  beforeEach(() => {
    vi.mocked(getAssets).mockResolvedValue([samsung]);
    vi.mocked(searchAssets).mockResolvedValue([samsung]);
    vi.mocked(addWatchlist).mockResolvedValue({ ...samsung, watchlistId: 10, assetId: 1, createdAt: "2026-07-03T00:00:00" });
  });

  it("renders assets and adds one to the watchlist", async () => {
    const user = userEvent.setup();
    renderCatalog();

    expect(await screen.findByText("삼성전자")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "관심 추가" }));

    expect(addWatchlist).toHaveBeenCalledWith(1);
    expect(await screen.findByRole("status")).toHaveTextContent("관심자산에 추가했습니다");
  });

  it("searches with a keyword and asset type", async () => {
    const user = userEvent.setup();
    renderCatalog();
    await screen.findByText("삼성전자");

    await user.type(screen.getByLabelText("자산 검색어"), "삼성");
    await user.selectOptions(screen.getByLabelText("자산 유형"), "STOCK");
    await user.click(screen.getByRole("button", { name: "검색" }));

    expect(searchAssets).toHaveBeenCalledWith("삼성", "STOCK");
  });
});
