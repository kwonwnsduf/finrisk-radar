"use client";

import { useEffect, useState } from "react";
import { ExternalLink, FileSearch } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { RiskHighlight } from "@/components/documents/risk-highlight";
import {
  getDocumentMatches,
  getDocuments,
  type DocumentItem,
  type DocumentRiskMatch,
} from "@/lib/api/documents";

export function DocumentRiskSection({ assetId }: { assetId: number }) {
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [matches, setMatches] = useState<DocumentRiskMatch[]>([]);
  useEffect(() => {
    void getDocuments(assetId)
      .then(setDocuments)
      .catch(() => setDocuments([]));
  }, [assetId]);
  async function inspect(id: number) {
    setSelected(id);
    setMatches(await getDocumentMatches(id));
  }
  return (
    <section className="mt-6 space-y-4">
      <div>
        <h2 className="text-xl font-bold text-slate-950">
          문서 기반 위험 근거
        </h2>
        <p className="text-sm text-slate-500">
          Naver 뉴스 제목·설명과 OpenDART 공시 원문에서 탐지한 근거입니다.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-base">최근 문서</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {documents.length ? (
            documents.map((document) => (
              <button
                key={document.id}
                type="button"
                onClick={() => void inspect(document.id)}
                className="flex w-full items-start justify-between gap-4 rounded-lg border p-3 text-left hover:bg-slate-50"
              >
                <div>
                  <p className="font-semibold text-slate-900">
                    {document.title}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {document.sourceType} ·{" "}
                    {document.publishedAt?.slice(0, 10) ?? "발행일 미상"} · 위험
                    근거 {document.riskMatchCount}건
                  </p>
                </div>
                <FileSearch className="mt-1 size-4 shrink-0 text-blue-600" />
              </button>
            ))
          ) : (
            <p className="text-sm text-slate-500">수집된 문서가 없습니다.</p>
          )}
        </CardContent>
      </Card>
      {selected !== null ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">위험 문장</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {matches.length ? (
              matches.map((match) => (
                <div key={match.id} className="rounded-lg border p-3">
                  <RiskHighlight match={match} />
                  <div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
                    <span>{match.eventType}</span>
                    <span>{match.assertionType}</span>
                    <span>신뢰도 {(match.confidence * 100).toFixed(0)}%</span>
                    {match.extractedAmount !== null ? (
                      <span className="font-semibold text-slate-700">
                        {match.amountOriginalText} ({match.extractedCurrency}{" "}
                        {Number(match.extractedAmount).toLocaleString()})
                      </span>
                    ) : null}
                  </div>
                </div>
              ))
            ) : (
              <p className="text-sm text-slate-500">위험 문장이 없습니다.</p>
            )}
            {documents.find((item) => item.id === selected)?.sourceUrl ? (
              <a
                className="inline-flex items-center gap-1 text-sm text-blue-600"
                href={
                  documents.find((item) => item.id === selected)?.sourceUrl ??
                  "#"
                }
                target="_blank"
                rel="noreferrer"
              >
                원문 출처 <ExternalLink className="size-3" />
              </a>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </section>
  );
}
