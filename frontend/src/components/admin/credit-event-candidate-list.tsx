"use client";

import { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { RiskHighlight } from "@/components/documents/risk-highlight";
import {
  getCandidates,
  reviewCandidate,
  type CreditEventCandidate,
} from "@/lib/api/documents";

export function CreditEventCandidateList() {
  const [items, setItems] = useState<CreditEventCandidate[]>([]);
  const [error, setError] = useState<string | null>(null);
  async function refresh() {
    try {
      setItems(await getCandidates());
    } catch {
      setError("후보 목록을 불러오지 못했습니다.");
    }
  }
  useEffect(() => {
    void getCandidates()
      .then(setItems)
      .catch(() => setError("후보 목록을 불러오지 못했습니다."));
  }, []);
  async function review(id: number, action: "approve" | "reject") {
    try {
      await reviewCandidate(id, action);
      await refresh();
    } catch {
      setError("후보 검토를 처리하지 못했습니다.");
    }
  }
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">CreditEvent 후보 검토</h1>
        <p className="text-sm text-slate-500">
          문서 근거를 확인한 뒤 기존 위험 엔진에 반영할 사건만 승인합니다.
        </p>
      </div>
      {error ? (
        <p
          role="alert"
          className="rounded-lg bg-red-50 p-3 text-sm text-red-700"
        >
          {error}
        </p>
      ) : null}
      {items.map((candidate) => (
        <Card key={candidate.id}>
          <CardHeader>
            <CardTitle className="text-base">
              {candidate.eventType} · Asset #{candidate.assetId}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-xs text-slate-500">
              {candidate.eventDate} · {candidate.severity} · 신뢰도{" "}
              {(candidate.confidence * 100).toFixed(0)}%
            </p>
            {candidate.matches.map((match) => (
              <div key={match.id} className="rounded-lg bg-slate-50 p-3">
                <RiskHighlight match={match} />
                {match.extractedAmount !== null ? (
                  <p className="mt-1 text-xs font-semibold">
                    {match.amountOriginalText} → {match.extractedCurrency}{" "}
                    {Number(match.extractedAmount).toLocaleString()}
                  </p>
                ) : null}
              </div>
            ))}
            <div className="flex gap-2">
              <Button onClick={() => void review(candidate.id, "approve")}>
                <Check className="size-4" />
                승인
              </Button>
              <Button
                variant="outline"
                onClick={() => void review(candidate.id, "reject")}
              >
                <X className="size-4" />
                반려
              </Button>
            </div>
          </CardContent>
        </Card>
      ))}
      {!items.length ? (
        <Card>
          <CardContent className="py-6 text-sm text-slate-500">
            검토 대기 후보가 없습니다.
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
