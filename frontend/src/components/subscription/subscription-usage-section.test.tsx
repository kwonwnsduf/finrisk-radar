import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SubscriptionUsageSection } from "@/components/subscription/subscription-usage-section";

vi.mock("@/lib/api/subscriptions", () => ({
  getMySubscription: vi.fn().mockResolvedValue({ plan: "FREE" }),
}));
vi.mock("@/lib/api/usage", () => ({
  getMyUsage: vi.fn().mockResolvedValue({
    plan: "FREE",
    backtest: { used: 2, limit: 5 },
    riskReport: { used: 1, limit: 3 },
    aiAgent: { used: 0, limit: 3 },
    watchlist: { used: 4, limit: 5 },
  }),
}));

describe("SubscriptionUsageSection", () => {
  it("renders the plan and all usage categories", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <SubscriptionUsageSection />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("FREE")).toBeInTheDocument();
    expect(screen.getByText("백테스트")).toBeInTheDocument();
    expect(screen.getByText("2 / 5")).toBeInTheDocument();
    expect(screen.getByText("관심 자산")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "프리미엄 전환" })).toBeDisabled();
  });
});
