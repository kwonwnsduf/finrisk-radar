import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { WatchlistContent } from "@/components/watchlist/watchlist-content";
import { deleteWatchlist, getWatchlists } from "@/lib/api/watchlists";

vi.mock("@/lib/api/watchlists", () => ({ getWatchlists: vi.fn(), deleteWatchlist: vi.fn() }));

describe("WatchlistContent", () => {
  it("renders and deletes the current user's watchlist item", async () => {
    vi.mocked(getWatchlists)
      .mockResolvedValueOnce([{
        watchlistId: 10, assetId: 1, name: "삼성전자", ticker: "005930", market: "KOSPI",
        sector: "Semiconductor", country: "KR", currency: "KRW", assetType: "STOCK",
        createdAt: "2026-07-03T00:00:00",
      }])
      .mockResolvedValueOnce([]);
    vi.mocked(deleteWatchlist).mockResolvedValue();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const user = userEvent.setup();

    render(<QueryClientProvider client={client}><WatchlistContent /></QueryClientProvider>);
    expect(await screen.findByText("삼성전자")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "삭제" }));

    expect(deleteWatchlist).toHaveBeenCalledWith(10);
    await waitFor(() => expect(screen.getByText("아직 등록한 관심자산이 없습니다.")).toBeInTheDocument());
  });
});
