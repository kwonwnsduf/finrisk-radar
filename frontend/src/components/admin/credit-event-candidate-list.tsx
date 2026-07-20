"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Check, Database, RefreshCw, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { RiskHighlight } from "@/components/documents/risk-highlight";
import { apiErrorMessage } from "@/lib/api/error-message";
import { getAssets, type Asset } from "@/lib/api/assets";
import {
  getCandidate,
  getCandidates,
  getDocumentCollectionJob,
  getRecentDocumentCollectionJobs,
  requestDocumentCollection,
  retryCandidateRecalculation,
  reviewCandidate,
  type CreditEventCandidate,
  type DocumentCollectionJob,
} from "@/lib/api/documents";
import { getRiskJob, type RiskJob } from "@/lib/api/risks";

interface TrackedCandidate {
  candidate: CreditEventCandidate;
  job: RiskJob | null;
}

const activeCollectionStatuses = new Set(["REQUESTED", "RUNNING"]);
const activeRiskStatuses = new Set(["COLLECTING", "REQUESTED", "RUNNING"]);

export function CreditEventCandidateList() {
  const [items, setItems] = useState<CreditEventCandidate[]>([]);
  const [tracked, setTracked] = useState<Record<number, TrackedCandidate>>({});
  const [assets, setAssets] = useState<Asset[]>([]);
  const [selectedAssetId, setSelectedAssetId] = useState<number | null>(null);
  const [collectionJobs, setCollectionJobs] = useState<DocumentCollectionJob[]>([]);
  const [collecting, setCollecting] = useState(false);
  const [reviewingId, setReviewingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function refreshCandidates() {
    setItems(await getCandidates());
  }

  useEffect(() => {
    void Promise.all([getCandidates(), getAssets(), getRecentDocumentCollectionJobs()])
      .then(([candidates, allAssets, jobs]) => {
        const eligible = allAssets.filter(
          (asset) => asset.assetType === "BOND_ISSUER" || asset.assetType === "REIT",
        );
        setItems(candidates);
        setAssets(eligible);
        setSelectedAssetId(eligible[0]?.id ?? null);
        setCollectionJobs(jobs);
      })
      .catch((loadError) =>
        setError(apiErrorMessage(loadError, "관리자 데이터를 불러오지 못했습니다.")),
      );
  }, []);

  const hasActiveCollection = collectionJobs.some((job) =>
    activeCollectionStatuses.has(job.status),
  );
  useEffect(() => {
    if (!hasActiveCollection) return;
    const timer = window.setInterval(() => {
      void Promise.all(
        collectionJobs.map((job) =>
          activeCollectionStatuses.has(job.status)
            ? getDocumentCollectionJob(job.jobId)
            : Promise.resolve(job),
        ),
      )
        .then(setCollectionJobs)
        .catch((pollError) =>
          setError(apiErrorMessage(pollError, "문서 수집 상태를 확인하지 못했습니다.")),
        );
    }, 2_000);
    return () => window.clearInterval(timer);
  }, [collectionJobs, hasActiveCollection]);

  const hasTrackedWork = Object.values(tracked).some(
    ({ candidate, job }) =>
      !candidate.recalculationJobId || !job || activeRiskStatuses.has(job.status),
  );
  useEffect(() => {
    if (!hasTrackedWork) return;
    const timer = window.setInterval(() => {
      void Promise.all(
        Object.values(tracked).map(async ({ candidate, job }) => {
          const nextCandidate = await getCandidate(candidate.id);
          const jobId = nextCandidate.recalculationJobId;
          const nextJob = jobId ? await getRiskJob(jobId) : job;
          return [nextCandidate.id, { candidate: nextCandidate, job: nextJob }] as const;
        }),
      )
        .then((entries) => setTracked(Object.fromEntries(entries)))
        .catch((pollError) =>
          setError(apiErrorMessage(pollError, "재계산 상태를 확인하지 못했습니다.")),
        );
    }, 2_000);
    return () => window.clearInterval(timer);
  }, [hasTrackedWork, tracked]);

  async function review(id: number, action: "approve" | "reject") {
    setError(null);
    setReviewingId(id);
    try {
      const candidate = await reviewCandidate(id, action);
      if (action === "approve") {
        setTracked((current) => ({
          ...current,
          [id]: { candidate, job: null },
        }));
      }
      await refreshCandidates();
    } catch (reviewError) {
      setError(apiErrorMessage(reviewError, "후보 검토를 처리하지 못했습니다."));
    } finally {
      setReviewingId(null);
    }
  }

  async function collect() {
    if (!selectedAssetId) return;
    setError(null);
    setCollecting(true);
    const toDate = localDate(new Date());
    const from = new Date();
    from.setDate(from.getDate() - 1);
    try {
      const jobs = await requestDocumentCollection(
        selectedAssetId,
        ["NAVER_NEWS", "OPEN_DART"],
        localDate(from),
        toDate,
      );
      setCollectionJobs((current) => [...jobs, ...current]);
    } catch (collectionError) {
      setError(apiErrorMessage(collectionError, "문서 수집을 요청하지 못했습니다."));
    } finally {
      setCollecting(false);
    }
  }

  async function retry(candidate: CreditEventCandidate) {
    setError(null);
    try {
      const next = await retryCandidateRecalculation(candidate.id);
      setTracked((current) => ({
        ...current,
        [candidate.id]: { candidate: next, job: null },
      }));
    } catch (retryError) {
      setError(apiErrorMessage(retryError, "재계산을 다시 요청하지 못했습니다."));
    }
  }

  const recentJobs = useMemo(() => collectionJobs.slice(0, 10), [collectionJobs]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">문서 리스크 관리</h1>
        <p className="text-sm text-slate-500">
          문서를 수집하고, 탐지된 신용 이벤트 후보를 승인해 위험 점수를 자동 재계산합니다.
        </p>
      </div>

      {error ? (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">문서 수집 실행</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-col gap-2 sm:flex-row">
            <select
              value={selectedAssetId ?? ""}
              onChange={(event) => setSelectedAssetId(Number(event.target.value))}
              aria-label="수집 대상 자산"
              className="h-9 flex-1 rounded-md border border-slate-200 bg-white px-3 text-sm"
            >
              {assets.map((asset) => (
                <option key={asset.id} value={asset.id}>
                  {asset.name} ({asset.ticker})
                </option>
              ))}
            </select>
            <Button onClick={() => void collect()} disabled={!selectedAssetId || collecting}>
              <Database className="size-4" />
              {collecting ? "요청 중" : "최근 1일 뉴스·DART 수집"}
            </Button>
          </div>
          <div className="space-y-2">
            {recentJobs.map((job) => (
              <div key={job.jobId} className="rounded-lg border p-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span>
                    Asset #{job.assetId} · {job.sourceType} · {job.fromDate}~{job.toDate}
                  </span>
                  <Status value={job.status} />
                </div>
                {job.status === "COMPLETED" ? (
                  <p className="mt-1 text-slate-500">수집 문서 {job.documentCount}건</p>
                ) : null}
                {job.status === "FAILED" ? (
                  <p className="mt-1 text-red-700">
                    {job.failureCode}: {job.failureMessage}
                  </p>
                ) : null}
              </div>
            ))}
            {!recentJobs.length ? (
              <p className="text-sm text-slate-500">최근 문서 수집 작업이 없습니다.</p>
            ) : null}
          </div>
        </CardContent>
      </Card>

      {Object.values(tracked).map(({ candidate, job }) => (
        <Card key={candidate.id} className="border-blue-200 bg-blue-50/40">
          <CardContent className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="font-semibold">후보 #{candidate.id} 승인 및 재계산</p>
              <p className="text-sm text-slate-600">{recalculationMessage(candidate, job)}</p>
              {candidate.recalculationLastError ? (
                <p className="mt-1 text-sm text-red-700">{candidate.recalculationLastError}</p>
              ) : null}
            </div>
            <div className="flex gap-2">
              {candidate.recalculationStatus === "FAILED" ||
              candidate.recalculationStatus === "DEFERRED" ||
              job?.status === "FAILED" ? (
                <Button variant="outline" onClick={() => void retry(candidate)}>
                  <RefreshCw className="size-4" /> 다시 계산
                </Button>
              ) : null}
              {job?.status === "COMPLETED" ? (
                <Button asChild>
                  <Link href={`/assets/${candidate.assetId}`}>새 점수 보기</Link>
                </Button>
              ) : null}
            </div>
          </CardContent>
        </Card>
      ))}

      <section className="space-y-4">
        <div>
          <h2 className="text-lg font-bold">승인 대기 후보</h2>
          <p className="text-sm text-slate-500">문서 근거를 확인한 사건만 승인하세요.</p>
        </div>
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
                      {match.amountOriginalText} · {match.extractedCurrency}{" "}
                      {Number(match.extractedAmount).toLocaleString()}
                    </p>
                  ) : null}
                </div>
              ))}
              <div className="flex gap-2">
                <Button
                  onClick={() => void review(candidate.id, "approve")}
                  disabled={reviewingId === candidate.id}
                >
                  <Check className="size-4" /> 승인
                </Button>
                <Button
                  variant="outline"
                  onClick={() => void review(candidate.id, "reject")}
                  disabled={reviewingId === candidate.id}
                >
                  <X className="size-4" /> 반려
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
        {!items.length ? (
          <Card>
            <CardContent className="py-6 text-sm text-slate-500">
              검토 대기 중인 후보가 없습니다.
            </CardContent>
          </Card>
        ) : null}
      </section>
    </div>
  );
}

function Status({ value }: { value: string }) {
  const tone =
    value === "COMPLETED"
      ? "bg-emerald-100 text-emerald-700"
      : value === "FAILED"
        ? "bg-red-100 text-red-700"
        : "bg-blue-100 text-blue-700";
  return <span className={`rounded-full px-2 py-1 text-xs font-semibold ${tone}`}>{value}</span>;
}

function recalculationMessage(candidate: CreditEventCandidate, job: RiskJob | null) {
  if (!candidate.recalculationJobId) {
    return candidate.recalculationStatus === "FAILED"
      ? `자동 재계산 실패 (${candidate.recalculationAttemptCount}회 시도)`
      : "재계산 작업 생성 대기 중";
  }
  if (!job) return "재계산 상태 확인 중";
  if (job.status === "COLLECTING") return "계산에 필요한 원천 데이터를 수집·저장 중입니다.";
  if (job.status === "REQUESTED") return "데이터 준비가 끝나 계산 대기 중입니다.";
  if (job.status === "RUNNING") return "승인된 사건을 포함해 위험 점수를 계산 중입니다.";
  if (job.status === "FAILED") return job.failureMessage ?? "위험 점수 재계산에 실패했습니다.";
  return `재계산 완료 · Risk score #${job.riskScoreId ?? "-"}`;
}

function localDate(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}
