import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RagSearchWorkbench } from "@/components/rag/rag-search-workbench";
import { getAssets } from "@/lib/api/assets";
import { searchRag } from "@/lib/api/rag";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api/assets", () => ({ getAssets: vi.fn() }));
vi.mock("@/lib/api/rag", () => ({ searchRag: vi.fn() }));

function renderWorkbench() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <RagSearchWorkbench />
    </QueryClientProvider>,
  );
}

describe("RagSearchWorkbench", () => {
  beforeEach(() => {
    vi.mocked(getAssets).mockResolvedValue([]);
    vi.mocked(searchRag).mockResolvedValue([]);
  });

  it("submits a semantic search and renders the empty state", async () => {
    const user = userEvent.setup();
    renderWorkbench();
    await user.type(screen.getByLabelText("RAG 검색 질문"), "단기 상환 위험");
    await user.click(screen.getByRole("button", { name: "검색" }));
    expect(vi.mocked(searchRag).mock.calls[0][0]).toEqual(
      expect.objectContaining({
        query: "단기 상환 위험",
        limit: 8,
        minimumSimilarity: 0.65,
      }),
    );
    expect(
      await screen.findByText("조건에 맞는 근거 문서가 없습니다."),
    ).toBeInTheDocument();
  });

  it("does not invent a source link when sourceUrl is absent", async () => {
    vi.mocked(searchRag).mockResolvedValue([
      {
        chunkId: 1,
        documentId: 2,
        chunkIndex: 0,
        assets: [],
        documentTitle: "공시",
        chunkContent: "차입금 만기가 도래합니다.",
        similarity: 0.9,
        documentType: "DISCLOSURE",
        sourceType: "OPEN_DART",
        contentScope: "FULL_TEXT",
        sourceName: "OpenDART",
        sourceUrl: null,
        publishedAt: "2026-01-01T00:00:00",
        riskMatches: [],
      },
    ]);
    const user = userEvent.setup();
    renderWorkbench();
    await user.type(screen.getByLabelText("RAG 검색 질문"), "만기");
    await user.click(screen.getByRole("button", { name: "검색" }));
    expect(
      await screen.findByText("차입금 만기가 도래합니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /원문 보기/ }),
    ).not.toBeInTheDocument();
  });
});
