import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReportDetail } from "@/components/reports/report-detail";
import { getReport } from "@/lib/api/reports";

vi.mock("@/lib/api/reports", () => ({ getReport: vi.fn() }));

const base = {
  id: "r1",
  assetId: 1,
  backtestJobId: null,
  reportType: "RISK_ANALYSIS" as const,
  status: "COMPLETED" as const,
  currentStep: "COMPLETED" as const,
  question: "위험 분석",
  title: "위험 분석",
  content: "요약",
  model: "configured-model",
  promptVersion: "risk-analysis-v1",
  inputTokenCount: 10,
  outputTokenCount: 20,
  failureCode: null,
  failureMessage: null,
  requestedAt: "2026-01-01T00:00:00",
  completedAt: "2026-01-01T00:01:00",
};

function renderDetail() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ReportDetail id="r1" />
    </QueryClientProvider>,
  );
}

describe("ReportDetail", () => {
  beforeEach(() => vi.mocked(getReport).mockReset());

  it("renders structured evidence and only an actual source URL", async () => {
    vi.mocked(getReport).mockResolvedValue({
      ...base,
      structuredResult: {
        summary: "위험 요약",
        evidence: [
          {
            documentId: 1,
            chunkIndex: 0,
            sourceUrl: "https://example.com/source",
            excerpt: "근거",
          },
        ],
      },
    });
    renderDetail();

    expect(await screen.findByText("위험 요약")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /원문 보기/ })).toHaveAttribute(
      "href",
      "https://example.com/source",
    );
  });

  it("does not create a source link without a URL", async () => {
    vi.mocked(getReport).mockResolvedValue({
      ...base,
      structuredResult: {
        summary: "요약",
        evidence: [
          { documentId: 1, chunkIndex: 0, sourceUrl: null, excerpt: "근거" },
        ],
      },
    });
    renderDetail();

    expect((await screen.findAllByText("요약")).length).toBeGreaterThan(0);
    expect(
      screen.queryByRole("link", { name: /원문 보기/ }),
    ).not.toBeInTheDocument();
  });
});
