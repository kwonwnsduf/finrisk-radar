"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { ExternalLink, Search } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { getAssets } from "@/lib/api/assets";
import { apiErrorMessage } from "@/lib/api/error-message";
import { searchRag, type RagSearchRequest } from "@/lib/api/rag";

export function RagSearchWorkbench() {
  const params = useSearchParams();
  const assetParam = params.get("assetId");
  const initialAsset = assetParam ? Number(assetParam) : Number.NaN;
  const [query, setQuery] = useState("");
  const [assetId, setAssetId] = useState(
    Number.isFinite(initialAsset) ? String(initialAsset) : "",
  );
  const [documentType, setDocumentType] = useState("");
  const [sourceType, setSourceType] = useState("");
  const [publishedFrom, setPublishedFrom] = useState("");
  const [publishedTo, setPublishedTo] = useState("");
  const [minimumSimilarity, setMinimumSimilarity] = useState("0.65");
  const assets = useQuery({ queryKey: ["assets"], queryFn: getAssets });
  const search = useMutation({ mutationFn: searchRag });

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const request: RagSearchRequest = { query: query.trim(), limit: 8 };
    if (assetId) request.assetId = Number(assetId);
    if (documentType)
      request.documentType = documentType as RagSearchRequest["documentType"];
    if (sourceType)
      request.sourceType = sourceType as RagSearchRequest["sourceType"];
    if (publishedFrom) request.publishedFrom = publishedFrom;
    if (publishedTo) request.publishedTo = publishedTo;
    if (minimumSimilarity)
      request.minimumSimilarity = Number(minimumSimilarity);
    search.mutate(request);
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header>
        <p className="text-sm font-semibold text-blue-600">
          Day 12 · Semantic Search
        </p>
        <h1 className="mt-1 text-3xl font-bold text-slate-950">
          위험 근거 문서 검색
        </h1>
        <p className="mt-2 text-sm text-slate-500">
          뉴스와 공시의 의미가 유사한 문단을 pgvector로 검색합니다.
        </p>
      </header>

      <Card>
        <CardContent className="pt-6">
          <form className="space-y-4" onSubmit={submit}>
            <label className="block space-y-1.5">
              <span className="text-sm font-semibold text-slate-700">
                검색 질문
              </span>
              <div className="flex gap-2">
                <Input
                  aria-label="RAG 검색 질문"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  maxLength={500}
                  placeholder="예: 단기 차입금 상환 위험이 커진 근거"
                  required
                />
                <Button
                  type="submit"
                  disabled={search.isPending || !query.trim()}
                >
                  <Search className="size-4" />
                  검색
                </Button>
              </div>
            </label>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              <Filter label="자산">
                <select
                  aria-label="검색 자산"
                  className={selectClass}
                  value={assetId}
                  onChange={(e) => setAssetId(e.target.value)}
                >
                  <option value="">전체 자산</option>
                  {assets.data?.map((asset) => (
                    <option key={asset.id} value={asset.id}>
                      {asset.name}
                    </option>
                  ))}
                </select>
              </Filter>
              <Filter label="문서 유형">
                <select
                  aria-label="문서 유형"
                  className={selectClass}
                  value={documentType}
                  onChange={(e) => setDocumentType(e.target.value)}
                >
                  <option value="">전체</option>
                  <option value="NEWS">뉴스</option>
                  <option value="DISCLOSURE">공시</option>
                </select>
              </Filter>
              <Filter label="출처">
                <select
                  aria-label="문서 출처"
                  className={selectClass}
                  value={sourceType}
                  onChange={(e) => setSourceType(e.target.value)}
                >
                  <option value="">전체</option>
                  <option value="NAVER_NEWS">Naver 뉴스</option>
                  <option value="OPEN_DART">OpenDART</option>
                </select>
              </Filter>
              <Filter label="시작일">
                <Input
                  aria-label="발행 시작일"
                  type="date"
                  value={publishedFrom}
                  onChange={(e) => setPublishedFrom(e.target.value)}
                />
              </Filter>
              <Filter label="종료일">
                <Input
                  aria-label="발행 종료일"
                  type="date"
                  value={publishedTo}
                  onChange={(e) => setPublishedTo(e.target.value)}
                />
              </Filter>
            </div>
            <label className="flex max-w-xs items-center gap-3 text-sm text-slate-600">
              최소 유사도
              <Input
                aria-label="최소 유사도"
                className="w-28"
                type="number"
                min="0"
                max="1"
                step="0.05"
                value={minimumSimilarity}
                onChange={(e) => setMinimumSimilarity(e.target.value)}
              />
            </label>
          </form>
        </CardContent>
      </Card>

      {search.isPending ? <Loading /> : null}
      {search.isError ? (
        <p
          role="alert"
          className="rounded-xl bg-red-50 p-4 text-sm font-semibold text-red-700"
        >
          {apiErrorMessage(search.error, "검색하지 못했습니다.")}
        </p>
      ) : null}
      {search.isSuccess && search.data.length === 0 ? (
        <p className="rounded-xl border border-dashed bg-white p-10 text-center text-slate-500">
          조건에 맞는 근거 문서가 없습니다.
        </p>
      ) : null}
      <div className="space-y-4">
        {search.data?.map((item) => (
          <Card key={item.chunkId}>
            <CardHeader className="flex-row items-start justify-between gap-4">
              <div>
                <CardTitle className="text-base text-slate-950">
                  {item.documentTitle}
                </CardTitle>
                <p className="mt-1 text-xs text-slate-500">
                  {item.sourceName ?? item.sourceType} ·{" "}
                  {item.publishedAt?.slice(0, 10) ?? "발행일 미상"} · 유사도{" "}
                  {(item.similarity * 100).toFixed(1)}%
                </p>
              </div>
              <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">
                {item.contentScope}
              </span>
            </CardHeader>
            <CardContent className="space-y-3">
              {item.contentScope === "SNIPPET" ? (
                <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  뉴스 제목과 설명 기반 검색 결과이며 기사 전문이 아닙니다.
                </p>
              ) : null}
              <p className="whitespace-pre-wrap text-sm leading-7 text-slate-700">
                {item.chunkContent}
              </p>
              {item.riskMatches.length ? (
                <div className="rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-800">
                  <p className="font-semibold">탐지된 위험 근거</p>
                  {item.riskMatches.map((match) => (
                    <p key={match.id} className="mt-1">
                      {match.evidence || match.sentenceText}
                    </p>
                  ))}
                </div>
              ) : null}
              <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
                <span>
                  Chunk #{item.chunkIndex} ·{" "}
                  {item.assets.map((asset) => asset.name).join(", ") ||
                    "연결 자산 없음"}
                </span>
                {item.sourceUrl ? (
                  <a
                    className="inline-flex items-center gap-1 font-semibold text-blue-600"
                    href={item.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    원문 보기
                    <ExternalLink className="size-3" />
                  </a>
                ) : null}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

const selectClass =
  "h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-900 shadow-sm outline-none focus:border-blue-500";
function Filter({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="space-y-1.5">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      {children}
    </label>
  );
}
function Loading() {
  return (
    <div aria-label="검색 중" className="space-y-3">
      {[1, 2].map((value) => (
        <div
          key={value}
          className="h-40 animate-pulse rounded-2xl bg-slate-200"
        />
      ))}
    </div>
  );
}
